package com.core.service;

import com.core.dto.CandidateOrderResponse;
import com.core.dto.JobRequest;
import com.core.dto.JobResponse;
import com.core.dto.OrderResponse;
import com.core.dto.RoutePreviewRequest;
import com.core.dto.RoutePreviewResponse;
import com.core.dto.master.AddressInfoDto;
import com.core.entity.DeliveryJob;
import com.core.entity.DeliveryRouting;
import com.core.entity.Order;
import com.core.entity.OrderAddress;
import com.core.repository.DeliveryJobRepository;
import com.core.repository.DeliveryRoutingRepository;
import com.core.repository.OrderRepository;
import com.common.util.GeoDistance;
import com.core.service.clients.MasterDataService;
import com.master.entity.Subdistrict;
import com.master.entity.SubdistrictDistance;
import com.master.repository.SubdistrictDistanceRepository;
import com.master.repository.SubdistrictRepository;
import com.master.dto.GoogleRouteDistanceResponse;
import com.master.exception.GoogleRoutesQuotaExceededException;
import com.master.service.GoogleRoutesService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobService {

    private static final int MAX_ORDERS_PER_JOB = 200;
    private static final int MAX_CANDIDATE_ORDERS = 500;
    private static final int MAX_CANDIDATE_RESULTS = 10;
    private static final Set<String> PENDING_STATUSES = Set.of("NEW", "PENDING");
    // routing status ที่ถือว่าจุดนั้น "ปิดแล้ว" สำหรับการตัดสินว่างานจบหรือยัง
    private static final Set<String> CLOSED_STOP_STATUSES = Set.of("COMPLETED", "SKIPPED");

    private final DeliveryJobRepository deliveryJobRepository;
    private final DeliveryRoutingRepository deliveryRoutingRepository;
    private final OrderRepository orderRepository;
    private final SubdistrictRepository subdistrictRepository;
    private final SubdistrictDistanceRepository subdistrictDistanceRepository;
    private final MasterDataService masterDataService;
    private final GoogleRoutesService googleRoutesService;

    @Transactional
    public JobResponse createJob(JobRequest request, String username) {
        List<Long> orderIds = request.getOrderIds();
        if (orderIds == null || orderIds.isEmpty()) {
            throw new IllegalArgumentException("At least one order must be selected");
        }
        if (orderIds.size() > MAX_ORDERS_PER_JOB) {
            throw new IllegalArgumentException("A job cannot contain more than " + MAX_ORDERS_PER_JOB + " orders");
        }

        Subdistrict origin = resolveSubdistrict(request.getOriginSubdistrictCode(), "Origin");

        List<Order> orders = orderRepository.findAllById(orderIds);
        if (orders.size() != orderIds.size()) {
            throw new IllegalArgumentException("Some selected orders were not found");
        }
        Map<Long, Order> orderById = orders.stream().collect(Collectors.toMap(Order::getOrderId, Function.identity()));
        for (Long orderId : orderIds) {
            Order order = orderById.get(orderId);
            if (!PENDING_STATUSES.contains(order.getOrderStatus() == null ? "" : order.getOrderStatus().toUpperCase())) {
                throw new IllegalArgumentException("Order " + order.getOrderCode() + " is not pending delivery");
            }
        }

        DeliveryJob job = DeliveryJob.builder()
                .jobCode(generateJobCode())
                .jobName(request.getJobName())
                .originSubdistrictCode(origin.getCode())
                .originDistrictCode(origin.getDistrict().getCode())
                .originProvinceCode(origin.getDistrict().getProvince().getCode())
                .totalOrders(orderIds.size())
                .scheduledDate(request.getScheduledDate())
                .createdBy(username)
                .build();
        DeliveryJob savedJob = deliveryJobRepository.save(job);

        List<DeliveryRouting> routings = new ArrayList<>();
        int sequence = 1;
        for (Long orderId : orderIds) {
            Order order = orderById.get(orderId);
            routings.add(DeliveryRouting.builder()
                    .deliveryJob(savedJob)
                    .order(order)
                    .sequenceNo(sequence++)
                    .createdBy(username)
                    .build());
            order.setOrderStatus("IN_TRANSIT");
            order.setUpdatedBy(username);
        }
        deliveryRoutingRepository.saveAll(routings);
        orderRepository.saveAll(orders);

        return mapToResponse(savedJob, routings);
    }

    @Transactional
    public JobResponse updateJob(Long jobId, JobRequest request, String username) {
        List<Long> newOrderIds = request.getOrderIds();
        if (newOrderIds == null || newOrderIds.isEmpty()) {
            throw new IllegalArgumentException("At least one order must be selected");
        }
        if (newOrderIds.size() > MAX_ORDERS_PER_JOB) {
            throw new IllegalArgumentException("A job cannot contain more than " + MAX_ORDERS_PER_JOB + " orders");
        }
        if (new java.util.LinkedHashSet<>(newOrderIds).size() != newOrderIds.size()) {
            throw new IllegalArgumentException("Duplicate orders in the selected stop list");
        }

        DeliveryJob job = findActiveJob(jobId);
        Subdistrict origin = resolveSubdistrict(request.getOriginSubdistrictCode(), "Origin");

        List<DeliveryRouting> existingRoutings = deliveryRoutingRepository.findByDeliveryJobJobId(jobId);
        Map<Long, DeliveryRouting> existingByOrderId = existingRoutings.stream()
                .collect(Collectors.toMap(r -> r.getOrder().getOrderId(), Function.identity()));
        Set<Long> newOrderIdSet = Set.copyOf(newOrderIds);

        // จุดที่ไม่อยู่ในลิสต์ใหม่แล้ว — ลบ routing และคืนสถานะ order กลับเป็น pending (ยกเว้นจุดที่จัดส่งไปแล้ว)
        List<DeliveryRouting> toRemove = existingRoutings.stream()
                .filter(r -> !newOrderIdSet.contains(r.getOrder().getOrderId()))
                .toList();
        for (DeliveryRouting routing : toRemove) {
            if (!"COMPLETED".equalsIgnoreCase(routing.getRoutingStatus())) {
                Order order = routing.getOrder();
                order.setOrderStatus("NEW");
                order.setUpdatedBy(username);
                orderRepository.save(order);
            }
        }
        deliveryRoutingRepository.deleteAll(toRemove);

        // จุดที่เพิ่มใหม่ (ยังไม่เคยอยู่ใน routing เดิมของงานนี้) ต้องเป็น pending อยู่ก่อนเท่านั้น
        List<Long> orderIdsToAdd = newOrderIds.stream()
                .filter(id -> !existingByOrderId.containsKey(id))
                .toList();
        Map<Long, Order> newOrdersById = Map.of();
        if (!orderIdsToAdd.isEmpty()) {
            List<Order> ordersToAdd = orderRepository.findAllById(orderIdsToAdd);
            if (ordersToAdd.size() != orderIdsToAdd.size()) {
                throw new IllegalArgumentException("Some selected orders were not found");
            }
            for (Order order : ordersToAdd) {
                if (!PENDING_STATUSES.contains(order.getOrderStatus() == null ? "" : order.getOrderStatus().toUpperCase())) {
                    throw new IllegalArgumentException("Order " + order.getOrderCode() + " is not pending delivery");
                }
            }
            newOrdersById = ordersToAdd.stream().collect(Collectors.toMap(Order::getOrderId, Function.identity()));
        }

        List<DeliveryRouting> finalRoutings = new ArrayList<>();
        int sequence = 1;
        for (Long orderId : newOrderIds) {
            DeliveryRouting routing = existingByOrderId.get(orderId);
            if (routing != null) {
                routing.setSequenceNo(sequence++);
                routing.setUpdatedBy(username);
                finalRoutings.add(routing);
            } else {
                Order order = newOrdersById.get(orderId);
                order.setOrderStatus("IN_TRANSIT");
                order.setUpdatedBy(username);
                orderRepository.save(order);
                finalRoutings.add(DeliveryRouting.builder()
                        .deliveryJob(job)
                        .order(order)
                        .sequenceNo(sequence++)
                        .createdBy(username)
                        .build());
            }
        }
        deliveryRoutingRepository.saveAll(finalRoutings);

        job.setJobName(request.getJobName());
        job.setOriginSubdistrictCode(origin.getCode());
        job.setOriginDistrictCode(origin.getDistrict().getCode());
        job.setOriginProvinceCode(origin.getDistrict().getProvince().getCode());
        job.setTotalOrders(newOrderIds.size());
        job.setScheduledDate(request.getScheduledDate());
        job.setUpdatedBy(username);
        DeliveryJob savedJob = deliveryJobRepository.save(job);

        return mapToResponse(savedJob, finalRoutings);
    }

    @Transactional
    public void deleteJob(Long jobId, String username) {
        DeliveryJob job = findActiveJob(jobId);

        // งานที่จบแล้ว: order ของจุดที่ข้ามถูกคืนเป็น PENDING ไปแล้ว (อาจอยู่ในงานใหม่) — ห้ามแตะซ้ำ
        boolean jobCompleted = "COMPLETED".equalsIgnoreCase(job.getDeliveryStatus());
        List<DeliveryRouting> routings = deliveryRoutingRepository.findByDeliveryJobJobId(jobId);
        for (DeliveryRouting routing : routings) {
            if (!jobCompleted && !"COMPLETED".equalsIgnoreCase(routing.getRoutingStatus())) {
                Order order = routing.getOrder();
                order.setOrderStatus("NEW");
                order.setUpdatedBy(username);
                orderRepository.save(order);
            }
        }

        job.setStatus("I");
        job.setUpdatedBy(username);
        deliveryJobRepository.save(job);
    }

    private DeliveryJob findActiveJob(Long jobId) {
        DeliveryJob job = deliveryJobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));
        if (!"A".equals(job.getStatus())) {
            throw new IllegalArgumentException("Job not found: " + jobId);
        }
        return job;
    }

    @Transactional
    public JobResponse completeStop(Long jobId, Long orderId, String username) {
        DeliveryRouting routing = findStopOfOpenJob(jobId, orderId);

        // จุดที่ข้ามไว้ (SKIPPED) กลับมายืนยันส่งได้ ตราบใดที่งานยังไม่จบ
        routing.setRoutingStatus("COMPLETED");
        routing.setUpdatedBy(username);
        deliveryRoutingRepository.save(routing);

        Order order = routing.getOrder();
        order.setOrderStatus("COMPLETED");
        order.setDeliveryDate(LocalDateTime.now());
        order.setUpdatedBy(username);
        orderRepository.save(order);

        return completeJobIfNoOpenStops(routing.getDeliveryJob(), username);
    }

    @Transactional
    public JobResponse skipStop(Long jobId, Long orderId, String username) {
        DeliveryRouting routing = findStopOfOpenJob(jobId, orderId);
        if (CLOSED_STOP_STATUSES.contains(upper(routing.getRoutingStatus()))) {
            throw new IllegalArgumentException("Stop for order " + orderId + " is already " + routing.getRoutingStatus());
        }

        // order ยังอยู่ในงานนี้ (IN_TRANSIT) — จะคืนเป็น PENDING ตอนงานจบเท่านั้น
        routing.setRoutingStatus("SKIPPED");
        routing.setUpdatedBy(username);
        deliveryRoutingRepository.save(routing);

        return completeJobIfNoOpenStops(routing.getDeliveryJob(), username);
    }

    private DeliveryRouting findStopOfOpenJob(Long jobId, Long orderId) {
        DeliveryRouting routing = deliveryRoutingRepository.findByDeliveryJobJobIdAndOrderOrderId(jobId, orderId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Delivery stop not found for job " + jobId + " and order " + orderId));
        if ("COMPLETED".equalsIgnoreCase(routing.getDeliveryJob().getDeliveryStatus())) {
            throw new IllegalArgumentException("Job " + jobId + " is already completed");
        }
        return routing;
    }

    // งานจบเมื่อทุกจุด ส่งแล้ว หรือ ข้าม — order ของจุดที่ข้ามคืนเป็น PENDING เพื่อจัดเข้างานรอบถัดไปได้
    private JobResponse completeJobIfNoOpenStops(DeliveryJob job, String username) {
        List<DeliveryRouting> allRoutings = deliveryRoutingRepository.findByDeliveryJobJobIdOrderBySequenceNoAsc(job.getJobId());
        boolean noOpenStops = allRoutings.stream()
                .allMatch(r -> CLOSED_STOP_STATUSES.contains(upper(r.getRoutingStatus())));
        if (noOpenStops) {
            for (DeliveryRouting r : allRoutings) {
                if ("SKIPPED".equalsIgnoreCase(r.getRoutingStatus())) {
                    Order order = r.getOrder();
                    order.setOrderStatus("PENDING");
                    order.setUpdatedBy(username);
                    orderRepository.save(order);
                }
            }
            job.setDeliveryStatus("COMPLETED");
            job.setCompletedDate(LocalDateTime.now());
            job.setUpdatedBy(username);
            deliveryJobRepository.save(job);
        }

        return mapToResponse(job, allRoutings);
    }

    private static String upper(String value) {
        return value == null ? "" : value.toUpperCase();
    }

    @Transactional(readOnly = true)
    public List<JobResponse> listJobs() {
        List<DeliveryJob> jobs = deliveryJobRepository.findByStatusOrderByCreatedAtDesc("A");
        if (jobs.isEmpty()) {
            return List.of();
        }

        List<Long> jobIds = jobs.stream().map(DeliveryJob::getJobId).toList();
        Map<Long, List<DeliveryRouting>> routingsByJobId = deliveryRoutingRepository.findByDeliveryJobJobIdIn(jobIds)
                .stream()
                .collect(Collectors.groupingBy(r -> r.getDeliveryJob().getJobId()));

        return jobs.stream()
                .map(job -> mapToResponse(job, routingsByJobId.getOrDefault(job.getJobId(), List.of())))
                .toList();
    }

    /**
     * รายการ order ที่รอจัดส่ง — ถ้าไม่ระบุ {@code toSubdistrictCode} เรียงตามระยะทางใกล้สุดจาก
     * {@code fromSubdistrictCode} (ผู้เรียกส่งจุดต้นทาง หรือจุดส่งของล่าสุดที่เพิ่งเลือกไว้ เพื่อแนะนำจุดส่งของ
     * ถัดไปที่ใกล้ที่สุด) — ถ้าระบุ {@code toSubdistrictCode} (โหมดระบุปลายทาง) เรียงตาม detour
     * (ระยะ(ต้นทาง→order) + ระยะ(order→ปลายทาง) − ระยะ(ต้นทาง→ปลายทาง)) แทน เพื่อแนะนำ order ที่อยู่
     * "ระหว่างทาง" จากต้นทางไปปลายทางนั้น
     *
     * ทำงาน 2 ขั้นเพื่อไม่ต้องยิง Google กับทุกตำบลของ order สูงสุด 500 รายการ:
     * <ol>
     *   <li><b>ประมาณระยะ + คัดตัวเข้ารอบ</b> — ทุกช่วงใช้ค่าที่มีในตาราง {@code subdistrict_distances} (ส่วนใหญ่คือ
     *       เส้นตรง haversine) ถ้าไม่มีแถวใช้ระยะเส้นตรงจากพิกัดตำบลคำนวณสดแทน (ไม่มีพิกัด = ไม่ทราบ) แล้วเรียง
     *       เอา {@value #MAX_CANDIDATE_RESULTS} อันดับแรก (โหมดระบุปลายทางคัดด้วย detour)</li>
     *   <li><b>ยืนยันด้วยระยะถนนจริง</b> — เฉพาะตำบลของ order ที่เข้ารอบ เรียก {@link GoogleRoutesService#findRoute}
     *       เพื่อแทนค่าประมาณด้วยระยะถนนจาก Google (คู่ที่เคยเรียกแล้วอ่านจาก DB ไม่เสียโควตา, คู่ใหม่ถูกบันทึกให้เอง
     *       และนับโควตารายวัน) แล้วคำนวณ distance/detour และเรียงใหม่ ถ้า Google ใช้ไม่ได้ (โควตาเต็ม, error,
     *       ไม่มีพิกัด) ช่วงนั้นคงค่าประมาณจากขั้นที่ 1 ไว้ — ผลสุดท้ายเรียงจากใกล้จุดอ้างอิงไปไกล
     *       (โหมดระบุปลายทางใช้ detour เป็นเกณฑ์รอง)</li>
     * </ol>
     * จำนวนครั้งที่ยิง Google ต่อ request จึงไม่เกิน ~2×{@value #MAX_CANDIDATE_RESULTS}+1 และลดลงเรื่อยๆ เมื่อคู่ตำบล
     * ถูกเก็บไว้แล้ว — การเรียก Google และการบันทึกเกิดใน transaction แยก ({@code REQUIRES_NEW}) เพื่อไม่ให้ความล้มเหลว
     * ของแต่ละคู่มาทำให้ transaction (read-only) ของ method นี้ต้อง rollback ไปด้วย
     */
    @Transactional(readOnly = true)
    public List<CandidateOrderResponse> getCandidateOrders(String fromSubdistrictCode,
                                                             String toSubdistrictCode,
                                                             String filterDistrictCode,
                                                             String filterSubdistrictCode) {
        Subdistrict from = resolveSubdistrict(fromSubdistrictCode, "Reference point");
        Subdistrict to = StringUtils.hasText(toSubdistrictCode)
                ? resolveSubdistrict(toSubdistrictCode, "Destination")
                : null;

        Set<String> provinceCodes = to == null
                ? Set.of(from.getDistrict().getProvince().getCode())
                : Stream.of(from.getDistrict().getProvince().getCode(), to.getDistrict().getProvince().getCode())
                        .collect(Collectors.toSet());
        String districtFilter = StringUtils.hasText(filterDistrictCode) ? filterDistrictCode.trim() : null;
        String subdistrictFilter = StringUtils.hasText(filterSubdistrictCode) ? filterSubdistrictCode.trim() : null;

        List<Order> candidates = orderRepository
                .findCandidateOrdersForJob(provinceCodes, districtFilter, subdistrictFilter)
                .stream()
                .limit(MAX_CANDIDATE_ORDERS)
                .toList();
        if (candidates.isEmpty()) {
            return List.of();
        }

        Set<String> subdistrictCodes = candidates.stream()
                .map(o -> o.getAddress() != null ? o.getAddress().getSubdistrictCode() : null)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<String, Subdistrict> subdistrictByCode = subdistrictRepository.findAllByCodeIn(subdistrictCodes).stream()
                .collect(Collectors.toMap(Subdistrict::getCode, Function.identity()));
        Map<Long, Subdistrict> subdistrictById = subdistrictByCode.values().stream()
                .collect(Collectors.toMap(Subdistrict::getSubdistrictId, Function.identity(), (a, b) -> a));

        List<Long> allCandidateSubdistrictIds = subdistrictByCode.values().stream()
                .map(Subdistrict::getSubdistrictId)
                .toList();

        // ขั้นที่ 1: ประมาณระยะทุกช่วงจากตาราง/เส้นตรง แล้วคัดตัวเข้ารอบ
        Map<Long, BigDecimal> distanceFromOriginBySubdistrictId = estimateDistances(from,
                excluding(allCandidateSubdistrictIds, from), subdistrictById);
        Map<Long, BigDecimal> distanceToDestinationBySubdistrictId = new HashMap<>();
        BigDecimal originToDestinationKm = null;
        if (to != null) {
            distanceToDestinationBySubdistrictId = estimateDistances(to,
                    excluding(allCandidateSubdistrictIds, to), subdistrictById);
            originToDestinationKm = from.getSubdistrictId().equals(to.getSubdistrictId())
                    ? BigDecimal.ZERO
                    : estimateDistance(from, to);
        }
        List<CandidateOrderResponse> shortlist = rankCandidates(candidates, from, to, subdistrictByCode,
                distanceFromOriginBySubdistrictId, distanceToDestinationBySubdistrictId, originToDestinationKm, false);

        // ขั้นที่ 2: เฉพาะตัวที่เข้ารอบ แทนค่าประมาณด้วยระยะถนนจริงจาก Google แล้วเรียงใหม่
        Set<Long> shortlistOrderIds = shortlist.stream()
                .map(CandidateOrderResponse::getOrderId)
                .collect(Collectors.toSet());
        List<Order> shortlistOrders = candidates.stream()
                .filter(o -> shortlistOrderIds.contains(o.getOrderId()))
                .toList();
        Set<Long> shortlistSubdistrictIds = shortlistOrders.stream()
                .map(o -> o.getAddress() != null && o.getAddress().getSubdistrictCode() != null
                        ? subdistrictByCode.get(o.getAddress().getSubdistrictCode())
                        : null)
                .filter(Objects::nonNull)
                .map(Subdistrict::getSubdistrictId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        RoadDistanceLookup roadDistances = new RoadDistanceLookup();
        overrideWithRoadDistances(from, shortlistSubdistrictIds, subdistrictById, roadDistances,
                distanceFromOriginBySubdistrictId);
        if (to != null) {
            overrideWithRoadDistances(to, shortlistSubdistrictIds, subdistrictById, roadDistances,
                    distanceToDestinationBySubdistrictId);
            if (!from.getSubdistrictId().equals(to.getSubdistrictId())) {
                BigDecimal roadKm = roadDistances.find(from, to);
                if (roadKm != null) {
                    originToDestinationKm = roadKm;
                }
            }
        }
        return rankCandidates(shortlistOrders, from, to, subdistrictByCode,
                distanceFromOriginBySubdistrictId, distanceToDestinationBySubdistrictId, originToDestinationKm, true);
    }

    /**
     * จำลองเส้นทางที่จะขับจริงตามลำดับที่เลือก: ต้นทาง → จุดส่งที่ 1 → 2 → ... → ปลายทาง (ถ้าระบุ) พร้อมระยะแต่ละช่วง
     * ใช้ตอน "preview" ก่อนบันทึกงาน ไม่เขียนข้อมูลงาน/order ใดๆ และไม่ตรวจสถานะ order (ใช้ได้ทั้งตอนสร้างและแก้ไขงาน)
     *
     * ระยะแต่ละช่วงหาเหมือน {@link #getCandidateOrders}: ตำบลเดียวกัน = 0, ไม่งั้นระยะถนนผ่าน
     * {@link GoogleRoutesService#findRoute} (ส่วนใหญ่ถูกเก็บไว้แล้วตอนเลือกจุด จึงไม่เสียโควตาเพิ่ม) ถ้าใช้ Google ไม่ได้
     * ถอยไปใช้ระยะเส้นตรง — ตำแหน่งทุกจุดคือจุดกลางตำบล ไม่ใช่ที่อยู่จริงของ order
     */
    @Transactional(readOnly = true)
    public RoutePreviewResponse previewRoute(RoutePreviewRequest request) {
        List<Long> orderIds = request.getOrderIds() == null ? List.of() : request.getOrderIds();
        if (orderIds.size() > MAX_ORDERS_PER_JOB) {
            throw new IllegalArgumentException("A job cannot contain more than " + MAX_ORDERS_PER_JOB + " orders");
        }
        Subdistrict origin = resolveSubdistrict(request.getOriginSubdistrictCode(), "Origin");
        Subdistrict destination = StringUtils.hasText(request.getDestinationSubdistrictCode())
                ? resolveSubdistrict(request.getDestinationSubdistrictCode(), "Destination")
                : null;

        Map<Long, Order> orderById = orderRepository.findAllById(orderIds).stream()
                .collect(Collectors.toMap(Order::getOrderId, Function.identity()));
        Set<String> subdistrictCodes = orderById.values().stream()
                .map(o -> o.getAddress() != null ? o.getAddress().getSubdistrictCode() : null)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<String, Subdistrict> subdistrictByCode = subdistrictCodes.isEmpty()
                ? Map.of()
                : subdistrictRepository.findAllByCodeIn(subdistrictCodes).stream()
                        .collect(Collectors.toMap(Subdistrict::getCode, Function.identity()));

        List<RoutePreviewResponse.Point> points = new ArrayList<>();
        List<Subdistrict> pointSubdistricts = new ArrayList<>();
        points.add(previewPoint(RoutePreviewResponse.PointType.ORIGIN, null, null, origin));
        pointSubdistricts.add(origin);
        int stopNo = 0;
        for (Long orderId : orderIds) {
            Order order = orderById.get(orderId);
            if (order == null) {
                throw new IllegalArgumentException("Order not found: " + orderId);
            }
            Subdistrict subdistrict = order.getAddress() != null && order.getAddress().getSubdistrictCode() != null
                    ? subdistrictByCode.get(order.getAddress().getSubdistrictCode())
                    : null;
            points.add(previewPoint(RoutePreviewResponse.PointType.STOP, ++stopNo, order, subdistrict));
            pointSubdistricts.add(subdistrict);
        }
        if (destination != null) {
            points.add(previewPoint(RoutePreviewResponse.PointType.DESTINATION, null, null, destination));
            pointSubdistricts.add(destination);
        }

        RoadDistanceLookup roadDistances = new RoadDistanceLookup();
        List<RoutePreviewResponse.Leg> legs = new ArrayList<>();
        BigDecimal totalKm = BigDecimal.ZERO;
        int unknownLegCount = 0;
        for (int i = 0; i + 1 < pointSubdistricts.size(); i++) {
            RoutePreviewResponse.Leg leg = previewLeg(i, pointSubdistricts.get(i), pointSubdistricts.get(i + 1), roadDistances);
            legs.add(leg);
            if (leg.getDistanceKm() == null) {
                unknownLegCount++;
            } else {
                totalKm = totalKm.add(leg.getDistanceKm());
            }
        }
        return RoutePreviewResponse.builder()
                .points(points)
                .legs(legs)
                .totalKm(totalKm)
                .unknownLegCount(unknownLegCount)
                .build();
    }

    private RoutePreviewResponse.Leg previewLeg(int fromIndex, Subdistrict from, Subdistrict to,
                                                RoadDistanceLookup roadDistances) {
        RoutePreviewResponse.Leg.LegBuilder leg = RoutePreviewResponse.Leg.builder()
                .fromIndex(fromIndex)
                .toIndex(fromIndex + 1);
        if (from == null || to == null) {
            return leg.method(RoutePreviewResponse.LegMethod.UNKNOWN).build();
        }
        if (from.getSubdistrictId().equals(to.getSubdistrictId())) {
            return leg.distanceKm(BigDecimal.ZERO).method(RoutePreviewResponse.LegMethod.SAME_SUBDISTRICT).build();
        }
        BigDecimal roadKm = roadDistances.find(from, to);
        if (roadKm != null) {
            return leg.distanceKm(roadKm).method(RoutePreviewResponse.LegMethod.ROAD).build();
        }
        BigDecimal estimateKm = estimateDistance(from, to);
        return estimateKm != null
                ? leg.distanceKm(estimateKm).method(RoutePreviewResponse.LegMethod.STRAIGHT_LINE).build()
                : leg.method(RoutePreviewResponse.LegMethod.UNKNOWN).build();
    }

    private RoutePreviewResponse.Point previewPoint(RoutePreviewResponse.PointType type, Integer stopNo,
                                                    Order order, Subdistrict subdistrict) {
        var district = subdistrict == null ? null : subdistrict.getDistrict();
        var province = district == null ? null : district.getProvince();
        return RoutePreviewResponse.Point.builder()
                .type(type)
                .stopNo(stopNo)
                .orderId(order == null ? null : order.getOrderId())
                .orderCode(order == null ? null : order.getOrderCode())
                .customerName(order == null || order.getCustomer() == null ? null : order.getCustomer().getName())
                .subdistrictCode(subdistrict == null
                        ? (order != null && order.getAddress() != null ? order.getAddress().getSubdistrictCode() : null)
                        : subdistrict.getCode())
                .subdistrictName(subdistrict == null ? null : subdistrict.getName())
                .districtName(district == null ? null : district.getName())
                .provinceName(province == null ? null : province.getName())
                .lat(subdistrict == null ? null : subdistrict.getLat())
                .lng(subdistrict == null ? null : subdistrict.getLng())
                .build();
    }

    private List<CandidateOrderResponse> rankCandidates(List<Order> orders, Subdistrict from, Subdistrict to,
                                                          Map<String, Subdistrict> subdistrictByCode,
                                                          Map<Long, BigDecimal> distanceFromOriginBySubdistrictId,
                                                          Map<Long, BigDecimal> distanceToDestinationBySubdistrictId,
                                                          BigDecimal originToDestinationKm,
                                                          boolean nearestFirst) {
        Comparator<CandidateOrderResponse> byDistance =
                Comparator.comparing(CandidateOrderResponse::getDistanceKm, Comparator.nullsLast(Comparator.naturalOrder()));
        Comparator<CandidateOrderResponse> byDetour =
                Comparator.comparing(CandidateOrderResponse::getDetourKm, Comparator.nullsLast(Comparator.<BigDecimal>naturalOrder()));
        // ไม่มีปลายทาง: เรียงตามระยะจากจุดอ้างอิงเสมอ
        // โหมดระบุปลายทาง: คัดตัวเข้ารอบด้วย detour (อยู่ "ระหว่างทาง") ก่อน (nearestFirst = false) แล้วเรียงผลสุดท้าย
        // จากใกล้จุดอ้างอิงไปไกล (nearestFirst = true) — ค่าเท่ากันใช้อีกเกณฑ์ตัดสิน
        Comparator<CandidateOrderResponse> ranking = to == null
                ? byDistance
                : (nearestFirst ? byDistance.thenComparing(byDetour) : byDetour.thenComparing(byDistance));
        return orders.stream()
                .map(order -> mapToCandidateResponse(order, from, to, subdistrictByCode,
                        distanceFromOriginBySubdistrictId, distanceToDestinationBySubdistrictId,
                        originToDestinationKm))
                .sorted(ranking)
                .limit(MAX_CANDIDATE_RESULTS)
                .toList();
    }

    private List<Long> excluding(List<Long> subdistrictIds, Subdistrict excluded) {
        return subdistrictIds.stream().filter(id -> !id.equals(excluded.getSubdistrictId())).toList();
    }

    /**
     * ระยะประมาณ (เส้นตรง) จาก {@code reference} ไปยังแต่ละตำบลใน {@code subdistrictIds}: ใช้แถวเส้นตรงในตาราง
     * {@code subdistrict_distances} ก่อน ถ้าไม่มีใช้ระยะเส้นตรงจากพิกัด ถ้าไม่มีพิกัดก็ไม่ใส่ key (= ไม่ทราบ) — ไม่เรียก Google
     *
     * แถวที่ {@code googleFetched = true} (ระยะถนน ยาวกว่าเส้นตรงเสมอ) ถูกข้ามตั้งใจ เพราะขั้นนี้ใช้คัดตัวเข้ารอบ
     * ถ้าเอาระยะถนนของบางตำบลมาเทียบกับเส้นตรงของตำบลอื่น ตัวที่ยังไม่เคยเรียก Google จะดูใกล้เกินจริงและถูกดึงเข้ารอบ
     * ทีละนิดทุก request (ยิง Google ไม่จบ) — ระยะถนนของตัวที่เข้ารอบจะถูกอ่านในขั้นที่ 2 แทน
     */
    private Map<Long, BigDecimal> estimateDistances(Subdistrict reference, List<Long> subdistrictIds,
                                                     Map<Long, Subdistrict> subdistrictById) {
        Map<Long, BigDecimal> distances = new HashMap<>(subdistrictDistanceRepository
                .findByOriginSubdistrictSubdistrictIdAndDestinationSubdistrictSubdistrictIdIn(
                        reference.getSubdistrictId(), subdistrictIds)
                .stream()
                .filter(sd -> !sd.isGoogleFetched())
                .collect(Collectors.toMap(
                        sd -> sd.getDestinationSubdistrict().getSubdistrictId(),
                        SubdistrictDistance::getDistanceKm)));
        for (Long id : subdistrictIds) {
            if (distances.containsKey(id)) {
                continue;
            }
            Subdistrict subdistrict = subdistrictById.get(id);
            BigDecimal straightLineKm = subdistrict == null ? null : straightLineKm(reference, subdistrict);
            if (straightLineKm != null) {
                distances.put(id, straightLineKm);
            }
        }
        return distances;
    }

    private BigDecimal estimateDistance(Subdistrict origin, Subdistrict destination) {
        return subdistrictDistanceRepository
                .findByOriginSubdistrictSubdistrictIdAndDestinationSubdistrictSubdistrictIdIn(
                        origin.getSubdistrictId(), List.of(destination.getSubdistrictId()))
                .stream()
                .filter(sd -> !sd.isGoogleFetched())
                .findFirst()
                .map(SubdistrictDistance::getDistanceKm)
                .orElseGet(() -> straightLineKm(origin, destination));
    }

    private BigDecimal straightLineKm(Subdistrict a, Subdistrict b) {
        return GeoDistance.haversineKm(a.getLat(), a.getLng(), b.getLat(), b.getLng());
    }

    /** แทนค่าประมาณของแต่ละตำบลใน {@code subdistrictIds} ด้วยระยะถนนจาก Google ถ้าหาได้ (ไม่ได้ = คงค่าเดิม) */
    private void overrideWithRoadDistances(Subdistrict reference, Set<Long> subdistrictIds,
                                            Map<Long, Subdistrict> subdistrictById,
                                            RoadDistanceLookup roadDistances,
                                            Map<Long, BigDecimal> distanceBySubdistrictId) {
        for (Long id : subdistrictIds) {
            Subdistrict subdistrict = subdistrictById.get(id);
            if (subdistrict == null || id.equals(reference.getSubdistrictId())) {
                continue;
            }
            BigDecimal roadKm = roadDistances.find(reference, subdistrict);
            if (roadKm != null) {
                distanceBySubdistrictId.put(id, roadKm);
            }
        }
    }

    /**
     * หาระยะถนนจริงระหว่างตำบลสองตำบลผ่าน {@link GoogleRoutesService#findRoute} (ซึ่งอ่านจาก
     * {@code subdistrict_distances} ก่อน และบันทึกผลกลับให้เองเมื่อต้องเรียก Google) — คืน {@code null}
     * (ไม่ throw) ถ้าหาไม่ได้ เพื่อให้ผู้เรียกคงค่าประมาณไว้ เมื่อเจอโควตารายวันเต็มครั้งแรก จะเลิกยิง Google
     * ในรอบ request นี้ และเหลือแค่อ่านคู่ที่เก็บไว้แล้วจากตาราง
     */
    private final class RoadDistanceLookup {

        private boolean quotaExhausted;

        BigDecimal find(Subdistrict origin, Subdistrict destination) {
            if (origin.getLat() == null || origin.getLng() == null
                    || destination.getLat() == null || destination.getLng() == null) {
                return null;
            }
            if (quotaExhausted) {
                return storedRoadDistanceKm(origin, destination);
            }
            try {
                GoogleRouteDistanceResponse route = googleRoutesService.findRoute(origin.getCode(), destination.getCode());
                return route == null ? null : route.getDistanceKm();
            } catch (GoogleRoutesQuotaExceededException ex) {
                quotaExhausted = true;
                log.warn("Google Routes daily limit reached -- keeping estimated distances for the rest of this request");
                return null;
            } catch (RuntimeException ex) {
                log.warn("Google Routes lookup failed for {} -> {}: {}", origin.getCode(), destination.getCode(),
                        ex.getMessage());
                return null;
            }
        }

        private BigDecimal storedRoadDistanceKm(Subdistrict origin, Subdistrict destination) {
            return subdistrictDistanceRepository
                    .findByOriginSubdistrictSubdistrictIdAndDestinationSubdistrictSubdistrictId(
                            origin.getSubdistrictId(), destination.getSubdistrictId())
                    .filter(SubdistrictDistance::isGoogleFetched)
                    .map(SubdistrictDistance::getDistanceKm)
                    .orElse(null);
        }
    }

    private CandidateOrderResponse mapToCandidateResponse(Order order, Subdistrict from, Subdistrict to,
                                                            Map<String, Subdistrict> subdistrictByCode,
                                                            Map<Long, BigDecimal> distanceFromOriginBySubdistrictId,
                                                            Map<Long, BigDecimal> distanceToDestinationBySubdistrictId,
                                                            BigDecimal originToDestinationKm) {
        OrderAddress address = order.getAddress();
        Subdistrict orderSubdistrict = address != null && address.getSubdistrictCode() != null
                ? subdistrictByCode.get(address.getSubdistrictCode())
                : null;

        BigDecimal distanceFromOriginKm = legDistanceKm(orderSubdistrict, from, distanceFromOriginBySubdistrictId);

        BigDecimal detourKm = null;
        if (to != null) {
            BigDecimal distanceToDestinationKm = legDistanceKm(orderSubdistrict, to, distanceToDestinationBySubdistrictId);
            if (distanceFromOriginKm != null && distanceToDestinationKm != null && originToDestinationKm != null) {
                detourKm = distanceFromOriginKm.add(distanceToDestinationKm).subtract(originToDestinationKm);
            }
        }

        return CandidateOrderResponse.builder()
                .orderId(order.getOrderId())
                .orderCode(order.getOrderCode())
                .customerName(order.getCustomer() != null ? order.getCustomer().getName() : null)
                .address(mapToAddressResponse(address))
                .orderStatus(order.getOrderStatus())
                .distanceKm(distanceFromOriginKm)
                .detourKm(detourKm)
                .build();
    }

    private BigDecimal legDistanceKm(Subdistrict orderSubdistrict, Subdistrict referencePoint,
                                      Map<Long, BigDecimal> distanceByReferenceSubdistrictId) {
        if (orderSubdistrict == null) {
            return null;
        }
        if (orderSubdistrict.getSubdistrictId().equals(referencePoint.getSubdistrictId())) {
            return BigDecimal.ZERO;
        }
        return distanceByReferenceSubdistrictId.get(orderSubdistrict.getSubdistrictId());
    }

    private OrderResponse.OrderAddressResponse mapToAddressResponse(OrderAddress address) {
        if (address == null) {
            return null;
        }
        AddressInfoDto addressInfo = masterDataService.getAddressInfoSafe(
                address.getProvinceCode(), address.getDistrictCode(), address.getSubdistrictCode());

        return OrderResponse.OrderAddressResponse.builder()
                .addressId(address.getAddressId())
                .addressLine(address.getAddressLine())
                .subdistrictCode(address.getSubdistrictCode())
                .subdistrictName(addressInfo.getSubdistrictName())
                .districtCode(address.getDistrictCode())
                .districtName(addressInfo.getDistrictName())
                .provinceCode(address.getProvinceCode())
                .provinceName(addressInfo.getProvinceName())
                .zipCode(address.getZipCode())
                .build();
    }

    private Subdistrict resolveSubdistrict(String code, String label) {
        if (!StringUtils.hasText(code)) {
            throw new IllegalArgumentException(label + " subdistrict code is required");
        }
        return subdistrictRepository.findByCode(code.trim())
                .orElseThrow(() -> new IllegalArgumentException(label + " subdistrict not found: " + code));
    }

    private String generateJobCode() {
        String datePart = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String uniquePart = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return "JOB-" + datePart + "-" + uniquePart;
    }

    private JobResponse mapToResponse(DeliveryJob job, List<DeliveryRouting> routings) {
        AddressInfoDto origin = masterDataService.getAddressInfoSafe(
                job.getOriginProvinceCode(), job.getOriginDistrictCode(), job.getOriginSubdistrictCode());

        List<JobResponse.RoutingItem> routingItems = routings.stream()
                .sorted(Comparator.comparing(DeliveryRouting::getSequenceNo))
                .map(r -> JobResponse.RoutingItem.builder()
                        .orderId(r.getOrder().getOrderId())
                        .orderCode(r.getOrder().getOrderCode())
                        .customerName(r.getOrder().getCustomer() != null ? r.getOrder().getCustomer().getName() : null)
                        .address(mapToAddressResponse(r.getOrder().getAddress()))
                        .sequenceNo(r.getSequenceNo())
                        .routingStatus(r.getRoutingStatus())
                        .build())
                .toList();

        return JobResponse.builder()
                .jobId(job.getJobId())
                .jobCode(job.getJobCode())
                .jobName(job.getJobName())
                .origin(origin)
                .totalOrders(job.getTotalOrders())
                .deliveryStatus(job.getDeliveryStatus())
                .scheduledDate(job.getScheduledDate())
                .createdAt(job.getCreatedAt())
                .routings(routingItems)
                .build();
    }
}
