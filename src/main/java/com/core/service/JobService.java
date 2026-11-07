package com.core.service;

import com.authen.entity.User;
import com.authen.repository.UserRepository;
import com.core.constant.JobAccess;
import com.core.constant.JobEventType;
import com.core.constant.StopReason;
import com.core.dto.GeoPoint;
import com.core.dto.JobEventResponse;
import com.core.dto.StopActionRequest;
import com.core.entity.DeliveryJobEvent;
import com.core.repository.DeliveryJobEventRepository;
import com.core.service.notification.TeamNotifier;
import com.core.service.team.TeamService;
import com.core.constant.JobStatus;
import com.core.constant.OrderStatus;
import com.core.dto.CandidateOrderResponse;
import com.core.dto.JobRequest;
import com.core.dto.JobResponse;
import com.core.dto.TeamUserSummary;
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
import com.core.service.team.JobAccessService;
import com.core.service.team.JobDriverRequestService;
import com.master.entity.Subdistrict;
import com.master.entity.SubdistrictDistance;
import com.master.repository.SubdistrictDistanceRepository;
import com.master.repository.SubdistrictRepository;
import com.master.dto.GoogleRouteDistanceResponse;
import com.master.exception.GoogleRoutesQuotaExceededException;
import com.master.service.GoogleRoutesService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
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
    private static final Set<String> PENDING_STATUSES = Set.of(OrderStatus.NEW, OrderStatus.PENDING);
    // routing status ของจุดส่ง (delivery_routing.routing_status)
    private static final String STOP_COMPLETED = "COMPLETED";
    private static final String STOP_SKIPPED = "SKIPPED";
    private static final String STOP_CANCELLED = "CANCELLED";
    // คนส่งแจ้งว่าลูกค้ายกเลิก รอเจ้าของอนุมัติ — order ยังไม่เปลี่ยน
    private static final String STOP_CANCEL_REQUESTED = "CANCEL_REQUESTED";
    // จุดที่จัดการแล้ว ไม่ต้องไปส่ง (ไม่ใช่ "จุดถัดไป") — จุดที่ไม่อยู่ในนี้คือจุดค้าง
    private static final Set<String> HANDLED_STOP_STATUSES =
            Set.of(STOP_COMPLETED, STOP_SKIPPED, STOP_CANCELLED, STOP_CANCEL_REQUESTED);
    // order ที่ถูกข้ามครบจำนวนนี้ (นับจากงานที่ปิดแล้ว) เป็น FAILED ตอนงานปิด แทนที่จะกลับไป PENDING
    static final int MAX_SKIPS_BEFORE_FAILED = 2;
    // ยกเลิก "ส่งแล้ว" ได้ภายในเวลานี้หลังกดยืนยัน (หน้าจอแสดงปุ่ม "เลิกทำ" 8 วินาที — เผื่อ network)
    static final Duration UNDO_COMPLETE_WINDOW = Duration.ofSeconds(60);
    // routing status ที่ order ของจุดนั้นจบไปแล้ว (ส่งแล้ว / ยกเลิก) — เอาจุดออกหรือลบงานต้องไม่คืนสถานะ order
    private static final Set<String> FINAL_STOP_STATUSES = Set.of("COMPLETED", "CANCELLED");

    private final DeliveryJobRepository deliveryJobRepository;
    private final DeliveryRoutingRepository deliveryRoutingRepository;
    private final OrderRepository orderRepository;
    private final SubdistrictRepository subdistrictRepository;
    private final SubdistrictDistanceRepository subdistrictDistanceRepository;
    private final MasterDataService masterDataService;
    private final GoogleRoutesService googleRoutesService;
    private final Clock clock;
    private final JobAccessService jobAccessService;
    private final JobDriverRequestService jobDriverRequestService;
    private final DeliveryJobEventRepository deliveryJobEventRepository;
    private final UserRepository userRepository;
    private final TeamNotifier teamNotifier;

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
        if (orders.size() != orderIds.size() || !orders.stream().allMatch(o -> isOwner(o.getCreatedBy(), username))) {
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
            // งานใหม่ยังไม่ออกรถ — order เป็น IN_TRANSIT ตอนเริ่มงาน
            order.setOrderStatus(OrderStatus.ASSIGNED);
            order.setUpdatedBy(username);
        }
        deliveryRoutingRepository.saveAll(routings);
        orderRepository.saveAll(orders);

        return mapToResponse(savedJob, routings, username);
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

        DeliveryJob job = findActiveJob(jobId, username);
        // งานที่จบแล้ว: order ของจุดที่ข้ามถูกคืนเป็น PENDING ไปแล้ว (อาจอยู่ในงานใหม่) — แก้ไขแล้วจะไปทับสถานะ order
        if ("COMPLETED".equalsIgnoreCase(job.getDeliveryStatus())) {
            throw new IllegalArgumentException("Job " + jobId + " is already completed and cannot be edited");
        }
        Subdistrict origin = resolveSubdistrict(request.getOriginSubdistrictCode(), "Origin");

        List<DeliveryRouting> existingRoutings = deliveryRoutingRepository.findByDeliveryJobJobId(jobId);
        Map<Long, DeliveryRouting> existingByOrderId = existingRoutings.stream()
                .collect(Collectors.toMap(r -> r.getOrder().getOrderId(), Function.identity()));
        Set<Long> newOrderIdSet = Set.copyOf(newOrderIds);

        // จุดที่ไม่อยู่ในลิสต์ใหม่แล้ว — ลบ routing และคืนสถานะ order กลับเป็น NEW (ยกเว้นจุดที่ส่งแล้ว/ยกเลิกแล้ว)
        List<DeliveryRouting> toRemove = existingRoutings.stream()
                .filter(r -> !newOrderIdSet.contains(r.getOrder().getOrderId()))
                .toList();
        for (DeliveryRouting routing : toRemove) {
            if (!FINAL_STOP_STATUSES.contains(upper(routing.getRoutingStatus()))) {
                Order order = routing.getOrder();
                order.setOrderStatus(OrderStatus.NEW);
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
            if (ordersToAdd.size() != orderIdsToAdd.size()
                    || !ordersToAdd.stream().allMatch(o -> isOwner(o.getCreatedBy(), username))) {
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
                order.setOrderStatus(JobStatus.IN_PROGRESS.equalsIgnoreCase(job.getDeliveryStatus())
                        ? OrderStatus.IN_TRANSIT : OrderStatus.ASSIGNED);
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
        record(jobId, null, JobEventType.JOB_EDITED, null, null, username);
        // เหลือแต่จุดที่ส่งแล้ว / ยกเลิกแล้ว → งานจบเลย เหมือนตอนส่งจุดสุดท้าย
        finishIfAllDone(job, finalRoutings, username);
        DeliveryJob savedJob = deliveryJobRepository.save(job);

        return mapToResponse(savedJob, finalRoutings, username);
    }

    @Transactional
    public void deleteJob(Long jobId, String username) {
        DeliveryJob job = findActiveJob(jobId, username);

        // งานที่จบแล้ว: order ของจุดที่ข้ามถูกคืนเป็น PENDING ไปแล้ว (อาจอยู่ในงานใหม่) — ห้ามแตะซ้ำ
        boolean jobCompleted = "COMPLETED".equalsIgnoreCase(job.getDeliveryStatus());
        List<DeliveryRouting> routings = deliveryRoutingRepository.findByDeliveryJobJobId(jobId);
        for (DeliveryRouting routing : routings) {
            if (!jobCompleted && !FINAL_STOP_STATUSES.contains(upper(routing.getRoutingStatus()))) {
                Order order = routing.getOrder();
                order.setOrderStatus(OrderStatus.NEW);
                order.setUpdatedBy(username);
                orderRepository.save(order);
            }
        }

        job.setStatus("I");
        job.setUpdatedBy(username);
        deliveryJobRepository.save(job);
        // คนส่งของงานนี้จะไม่เห็นงานแล้ว และคำขอที่รอตอบถูกยกเลิก (design assignConfirm G4) — แจ้งให้รู้
        jobAccessService.notifyUnassigned(job, job.getAssignedDriverId());
        jobDriverRequestService.cancelPending(job, username);
    }

    /**
     * Jobs and orders belong to the user who created them. Someone else's is reported exactly
     * like a missing one, so ids cannot be probed.
     */
    private static boolean isOwner(String createdBy, String username) {
        return username != null && username.equals(createdBy);
    }

    /** A job the caller may manage (edit, delete, assign a driver): the owner only. */
    private DeliveryJob findActiveJob(Long jobId, String username) {
        DeliveryJob job = deliveryJobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));
        if (!"A".equals(job.getStatus()) || !isOwner(job.getCreatedBy(), username)) {
            throw new IllegalArgumentException("Job not found: " + jobId);
        }
        return job;
    }

    /**
     * A job the caller may operate (start, stop actions, close): the owner, or the assigned driver while still an
     * ACTIVE member of the owner's team. Anyone else gets the same "not found" as a missing job.
     */
    private DeliveryJob findOperableJob(Long jobId, String username) {
        DeliveryJob job = deliveryJobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));
        if (!"A".equals(job.getStatus()) || !canOperate(job, username)) {
            throw new IllegalArgumentException("Job not found: " + jobId);
        }
        return job;
    }

    private boolean canOperate(DeliveryJob job, String username) {
        return isOwner(job.getCreatedBy(), username) || jobAccessService.isAssignedActiveDriver(job, username);
    }

    /**
     * เลือก / เปลี่ยน / ปลด คนส่งของงาน ({@code driverUserId = null} = ปลด) — เจ้าของงานเท่านั้น
     * เลือกคน = ส่งคำขอให้คนส่งตอบรับก่อน ไม่ได้งานทันที (design assignConfirm) — กฎอยู่ใน
     * {@link JobDriverRequestService#requestDriver} · ล็อกแถวงานก่อน ให้การเลือกกับการตอบรับของคนส่งทำทีละรายการ
     */
    @Transactional
    public JobResponse assignDriver(Long jobId, Integer driverUserId, String username) {
        DeliveryJob job = deliveryJobRepository.findForUpdate(jobId)
                .filter(j -> "A".equals(j.getStatus()) && isOwner(j.getCreatedBy(), username))
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));
        List<DeliveryRouting> routings = deliveryRoutingRepository.findByDeliveryJobJobIdOrderBySequenceNoAsc(jobId);
        int stopCount = (int) routings.stream().filter(r -> !"CANCELLED".equalsIgnoreCase(r.getRoutingStatus())).count();
        jobDriverRequestService.requestDriver(job, driverUserId, username, stopCount);
        return mapToResponse(job, routings, username);
    }

    @Transactional
    public JobResponse completeStop(Long jobId, Long orderId, String username) {
        return completeStop(jobId, orderId, null, username);
    }

    /** {@code location} = phone position when pressed (optional, kept in the history — driver-risk ข้อ 17, 18). */
    @Transactional
    public JobResponse completeStop(Long jobId, Long orderId, GeoPoint location, String username) {
        DeliveryRouting routing = findStopOfOpenJob(jobId, orderId, username);
        if (STOP_CANCELLED.equalsIgnoreCase(routing.getRoutingStatus())) {
            throw new IllegalArgumentException("Stop for order " + orderId + " is already " + routing.getRoutingStatus());
        }
        startIfNotStarted(routing.getDeliveryJob(), username);

        // จุดที่ข้ามไว้ (SKIPPED) / แจ้งยกเลิกไว้ (ลูกค้าเปลี่ยนใจอีกที) กลับมายืนยันส่งได้ ตราบใดที่งานยังไม่จบ
        setStopStatus(routing, STOP_COMPLETED, null, null, username);

        Order order = routing.getOrder();
        order.setOrderStatus(OrderStatus.COMPLETED);
        order.setDeliveryDate(LocalDateTime.now(clock));
        order.setUpdatedBy(username);
        orderRepository.save(order);

        recordAt(jobId, routing.getOrder(), JobEventType.STOP_COMPLETED, null, null, location, username);
        return finishJobIfAllDone(routing.getDeliveryJob(), username);
    }

    /**
     * ข้ามจุด — ต้องเลือกเหตุผล ({@link StopReason#SKIP_LABELS}) · order ยังอยู่ในงานนี้ (IN_TRANSIT) จนงานปิด
     * งานไม่ปิดเองเพราะการข้าม (risk ข้อ 3) — ถ้าไม่เหลือจุดค้าง คนส่งกลับไปส่งจุดที่ข้าม หรือกด "ปิดงาน" เอง
     */
    @Transactional
    public JobResponse skipStop(Long jobId, Long orderId, StopActionRequest body, String username) {
        Reason reason = requireReason(body, StopReason.SKIP_LABELS);
        DeliveryRouting routing = findStopOfOpenJob(jobId, orderId, username);
        if (!isOpen(routing)) {
            throw new IllegalArgumentException("Stop for order " + orderId + " is already " + routing.getRoutingStatus());
        }
        startIfNotStarted(routing.getDeliveryJob(), username);

        setStopStatus(routing, STOP_SKIPPED, reason.code(), reason.note(), username);
        recordAt(jobId, routing.getOrder(), JobEventType.STOP_SKIPPED, reason.code(), reason.note(), body.getLocation(),
                username);
        return finishJobIfAllDone(routing.getDeliveryJob(), username);
    }

    /**
     * เลิกทำ "ส่งแล้ว" (กดยืนยันผิดจุด) — ได้เฉพาะภายใน {@link #UNDO_COMPLETE_WINDOW} และงานยังไม่ปิด
     * (ถ้าการยืนยันนั้นทำให้งานปิด order ที่ข้ามถูกปล่อยเป็น PENDING ไปแล้ว อาจอยู่ในงานอื่น)
     * จุดกลับเป็น NEW (รอจัดส่ง) เสมอ แม้ก่อนยืนยันจะเป็นจุดที่ข้ามไว้
     */
    @Transactional
    public JobResponse reopenStop(Long jobId, Long orderId, String username) {
        DeliveryRouting routing = findStopOfOpenJob(jobId, orderId, username);
        if (!STOP_COMPLETED.equalsIgnoreCase(routing.getRoutingStatus())) {
            throw new IllegalArgumentException("Stop for order " + orderId + " is not completed");
        }
        Order order = routing.getOrder();
        LocalDateTime deliveredAt = order.getDeliveryDate();
        if (deliveredAt == null || deliveredAt.plus(UNDO_COMPLETE_WINDOW).isBefore(LocalDateTime.now(clock))) {
            throw new IllegalArgumentException("Stop for order " + orderId + " can no longer be undone");
        }

        setStopStatus(routing, "NEW", null, null, username);

        order.setOrderStatus(OrderStatus.IN_TRANSIT);
        order.setDeliveryDate(null);
        order.setUpdatedBy(username);
        orderRepository.save(order);

        record(jobId, orderId, JobEventType.STOP_UNDONE, null, null, username);
        return finishJobIfAllDone(routing.getDeliveryJob(), username);
    }

    /**
     * ลูกค้ายกเลิก — ต้องเลือกเหตุผล ({@link StopReason#CANCEL_LABELS})
     * <ul>
     *   <li>เจ้าของกด → ยกเลิกทันที: จุดและ order เป็น CANCELLED · ถ้าทุกจุดส่งแล้ว/ยกเลิกแล้ว งานปิด</li>
     *   <li>คนส่งกด → แค่ "แจ้งยกเลิก" (CANCEL_REQUESTED) order ไม่เปลี่ยน รอเจ้าของอนุมัติ (risk ข้อ 2, 11)</li>
     * </ul>
     */
    @Transactional
    public JobResponse cancelStop(Long jobId, Long orderId, StopActionRequest body, String username) {
        Reason reason = requireReason(body, StopReason.CANCEL_LABELS);
        DeliveryRouting routing = findStopOfOpenJob(jobId, orderId, username);
        String status = upper(routing.getRoutingStatus());
        if (!isOpen(routing) && !STOP_SKIPPED.equals(status)) {
            throw new IllegalArgumentException("Stop for order " + orderId + " is already " + routing.getRoutingStatus());
        }
        DeliveryJob job = routing.getDeliveryJob();
        startIfNotStarted(job, username);

        if (isOwner(job.getCreatedBy(), username)) {
            cancelOrderOfStop(routing, reason.code(), reason.note(), username);
            recordAt(jobId, routing.getOrder(), JobEventType.STOP_CANCELLED, reason.code(), reason.note(),
                    body.getLocation(), username);
            return finishJobIfAllDone(job, username);
        }

        setStopStatus(routing, STOP_CANCEL_REQUESTED, reason.code(), reason.note(), username);
        recordAt(jobId, routing.getOrder(), JobEventType.STOP_CANCEL_REQUESTED, reason.code(), reason.note(),
                body.getLocation(), username);
        User owner = userRepository.findByUsername(job.getCreatedBy()).orElse(null);
        User driver = userRepository.findByUsername(username).orElse(null);
        teamNotifier.stopCancelRequested(job, owner, driver, customerName(routing.getOrder()),
                StopReason.text(reason.code(), reason.note()));
        return finishJobIfAllDone(job, username);
    }

    /** เจ้าของอนุมัติที่คนส่งแจ้งยกเลิก → จุดและ order เป็น CANCELLED (เหตุผลเดิมของคนส่งยังอยู่) */
    @Transactional
    public JobResponse approveCancel(Long jobId, Long orderId, String username) {
        DeliveryRouting routing = findCancelRequestOfOwner(jobId, orderId, username);
        cancelOrderOfStop(routing, routing.getStatusReason(), routing.getStatusNote(), username);
        record(jobId, orderId, JobEventType.STOP_CANCEL_APPROVED, null, null, username);
        notifyDriverOfCancelAnswer(routing, true);
        return finishJobIfAllDone(routing.getDeliveryJob(), username);
    }

    /** เจ้าของไม่อนุมัติ → จุดกลับเป็นรอจัดส่ง คนส่งต้องไปส่งต่อ (หรือข้าม) */
    @Transactional
    public JobResponse rejectCancel(Long jobId, Long orderId, String username) {
        DeliveryRouting routing = findCancelRequestOfOwner(jobId, orderId, username);
        setStopStatus(routing, "NEW", null, null, username);
        record(jobId, orderId, JobEventType.STOP_CANCEL_REJECTED, null, null, username);
        notifyDriverOfCancelAnswer(routing, false);
        return finishJobIfAllDone(routing.getDeliveryJob(), username);
    }

    /**
     * ปิดงานเอง (เช่น จบวันแล้วยังส่งไม่ครบ) — จุดที่ยังค้างถูกบันทึกว่าข้าม (เหตุผล "ปิดงานก่อนไปส่ง")
     * จุดที่แจ้งยกเลิกแต่เจ้าของยังไม่อนุมัติ ก็นับเป็นข้าม แล้วใช้กฎปิดงานเดิม: order ของจุดที่ข้ามคืนเป็น PENDING
     */
    @Transactional
    public JobResponse closeJob(Long jobId, String username) {
        DeliveryJob job = findOperableJob(jobId, username);
        if ("COMPLETED".equalsIgnoreCase(job.getDeliveryStatus())) {
            throw new IllegalArgumentException("Job " + jobId + " is already completed");
        }
        // งานที่ยังไม่ออกรถ ปิดไม่ได้ — ทุกจุดจะถูกนับเป็นการข้าม (และอาจทำให้ order เป็น FAILED) ทั้งที่ยังไม่ได้ไปส่ง
        if (!JobStatus.IN_PROGRESS.equalsIgnoreCase(job.getDeliveryStatus())) {
            throw new IllegalArgumentException("Job " + jobId + " has not started yet; delete it instead");
        }

        List<DeliveryRouting> routings = deliveryRoutingRepository.findByDeliveryJobJobIdOrderBySequenceNoAsc(jobId);
        List<DeliveryRouting> changed = new ArrayList<>();
        for (DeliveryRouting routing : routings) {
            if (isOpen(routing)) {
                routing.setRoutingStatus(STOP_SKIPPED);
                routing.setStatusReason(StopReason.JOB_CLOSED);
                routing.setStatusNote(null);
            } else if (STOP_CANCEL_REQUESTED.equalsIgnoreCase(routing.getRoutingStatus())) {
                routing.setRoutingStatus(STOP_SKIPPED);
                routing.setStatusReason(StopReason.CANCEL_NOT_APPROVED);
                routing.setStatusNote(null);
            } else {
                continue;
            }
            routing.setUpdatedBy(username);
            changed.add(routing);
        }
        deliveryRoutingRepository.saveAll(changed);

        closeAndReleaseSkipped(job, routings, username);
        deliveryJobRepository.save(job);
        record(jobId, null, JobEventType.JOB_CLOSED, null, null, username);
        return mapToResponse(job, routings, username);
    }

    /**
     * เริ่มงาน (ออกรถ) — งาน NEW → IN_PROGRESS และ order ของจุดที่ยังค้าง ASSIGNED → IN_TRANSIT
     */
    @Transactional
    public JobResponse startJob(Long jobId, String username) {
        DeliveryJob job = findOperableJob(jobId, username);
        if ("COMPLETED".equalsIgnoreCase(job.getDeliveryStatus())) {
            throw new IllegalArgumentException("Job " + jobId + " is already completed");
        }
        if (JobStatus.IN_PROGRESS.equalsIgnoreCase(job.getDeliveryStatus())) {
            throw new IllegalArgumentException("Job " + jobId + " has already started");
        }
        startIfNotStarted(job, username);
        deliveryJobRepository.save(job);
        return mapToResponse(job, deliveryRoutingRepository.findByDeliveryJobJobIdOrderBySequenceNoAsc(jobId), username);
    }

    /** ประวัติของงาน ใหม่สุดก่อน — เจ้าของและคนส่งที่ได้รับงานเห็นเหมือนกัน (risk ข้อ 18) */
    @Transactional(readOnly = true)
    public List<JobEventResponse> listEvents(Long jobId, String username) {
        DeliveryJob job = findOperableJob(jobId, username);
        List<DeliveryJobEvent> events = deliveryJobEventRepository.findByJobIdOrderByEventIdDesc(jobId);
        if (events.isEmpty()) {
            return List.of();
        }
        Map<String, User> actors = userRepository.findByUsernameIn(
                        events.stream().map(DeliveryJobEvent::getActorUsername).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(User::getUsername, Function.identity()));
        Set<Long> orderIds = events.stream().map(DeliveryJobEvent::getOrderId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> customers = new HashMap<>();
        if (!orderIds.isEmpty()) {
            orderRepository.findAllById(orderIds).forEach(o -> customers.put(o.getOrderId(), customerName(o)));
        }
        return events.stream()
                .map(e -> JobEventResponse.builder()
                        .eventId(e.getEventId())
                        .eventType(e.getEventType())
                        .orderId(e.getOrderId())
                        .customerName(e.getOrderId() == null ? null : customers.get(e.getOrderId()))
                        .reason(e.getReason())
                        .reasonText(StopReason.text(e.getReason(), e.getNote()))
                        .actor(actors.containsKey(e.getActorUsername())
                                ? TeamService.toSummary(actors.get(e.getActorUsername()))
                                : TeamUserSummary.builder().username(e.getActorUsername())
                                        .displayName(e.getActorUsername()).build())
                        .byOwner(isOwner(job.getCreatedBy(), e.getActorUsername()))
                        .createdAt(e.getCreatedAt())
                        .lat(e.getLat())
                        .lng(e.getLng())
                        .accuracyM(e.getAccuracyM())
                        .distanceKm(e.getDistanceKm())
                        .build())
                .toList();
    }

    /**
     * คนส่งกดส่ง/ข้าม/ยกเลิกจุดโดยยังไม่กด "เริ่มงาน" → เริ่มงานให้อัตโนมัติ (ไม่บล็อกคนส่งที่ลืมกด)
     * ผู้เรียกต้อง save job เอง (หรือปล่อยให้ dirty checking ภายใน transaction)
     */
    private void startIfNotStarted(DeliveryJob job, String username) {
        if (!JobStatus.NEW.equalsIgnoreCase(job.getDeliveryStatus())) {
            return;
        }
        job.setDeliveryStatus(JobStatus.IN_PROGRESS);
        job.setUpdatedBy(username);
        deliveryJobRepository.save(job);
        record(job.getJobId(), null, JobEventType.JOB_STARTED, null, null, username);
        // เจ้าของเริ่มเองระหว่างรอคนส่งตอบ = ส่งเอง → คำขอถูกยกเลิก (G4) · มีคนส่งรับแล้วจะไม่มีคำขอค้าง
        jobDriverRequestService.cancelPending(job, username);
        for (DeliveryRouting r : deliveryRoutingRepository.findByDeliveryJobJobIdOrderBySequenceNoAsc(job.getJobId())) {
            Order order = r.getOrder();
            if (OrderStatus.ASSIGNED.equals(order.getOrderStatus())) {
                order.setOrderStatus(OrderStatus.IN_TRANSIT);
                order.setUpdatedBy(username);
                orderRepository.save(order);
            }
        }
    }

    private DeliveryRouting findStopOfOpenJob(Long jobId, Long orderId, String username) {
        DeliveryRouting routing = deliveryRoutingRepository.findByDeliveryJobJobIdAndOrderOrderId(jobId, orderId)
                .filter(r -> canOperate(r.getDeliveryJob(), username))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Delivery stop not found for job " + jobId + " and order " + orderId));
        DeliveryJob job = routing.getDeliveryJob();
        // งานที่ถูกลบ (soft-delete) — order ถูกคืนเป็น NEW ไปแล้ว (อาจอยู่ในงานใหม่) ห้ามแตะซ้ำ เช่นจากหน้ารายละเอียดที่เปิดค้างไว้
        if (!"A".equals(job.getStatus())) {
            throw new IllegalArgumentException("Job not found: " + jobId);
        }
        if ("COMPLETED".equalsIgnoreCase(job.getDeliveryStatus())) {
            throw new IllegalArgumentException("Job " + jobId + " is already completed");
        }
        return routing;
    }

    /** จุดที่คนส่งแจ้งยกเลิกไว้ ในงานที่ยังเปิดของเจ้าของ — คนอื่นได้ "not found" เหมือนไม่มีจุดนี้ */
    private DeliveryRouting findCancelRequestOfOwner(Long jobId, Long orderId, String username) {
        DeliveryRouting routing = findStopOfOpenJob(jobId, orderId, username);
        if (!isOwner(routing.getDeliveryJob().getCreatedBy(), username)) {
            throw new IllegalArgumentException("Delivery stop not found for job " + jobId + " and order " + orderId);
        }
        if (!STOP_CANCEL_REQUESTED.equalsIgnoreCase(routing.getRoutingStatus())) {
            throw new IllegalArgumentException("Stop for order " + orderId + " has no cancel request");
        }
        return routing;
    }

    private void cancelOrderOfStop(DeliveryRouting routing, String reason, String note, String username) {
        setStopStatus(routing, STOP_CANCELLED, reason, note, username);
        Order order = routing.getOrder();
        order.setOrderStatus(OrderStatus.CANCELLED);
        order.setUpdatedBy(username);
        orderRepository.save(order);
    }

    private void notifyDriverOfCancelAnswer(DeliveryRouting routing, boolean approved) {
        DeliveryJob job = routing.getDeliveryJob();
        if (job.getAssignedDriverId() == null) {
            return;
        }
        userRepository.findById(job.getAssignedDriverId()).ifPresent(driver ->
                teamNotifier.stopCancelAnswered(job, driver, customerName(routing.getOrder()), approved));
    }

    private void setStopStatus(DeliveryRouting routing, String status, String reason, String note, String username) {
        routing.setRoutingStatus(status);
        routing.setStatusReason(reason);
        routing.setStatusNote(note);
        routing.setUpdatedBy(username);
        deliveryRoutingRepository.save(routing);
    }

    private void record(Long jobId, Long orderId, String type, String reason, String note, String username) {
        deliveryJobEventRepository.save(DeliveryJobEvent.builder()
                .jobId(jobId)
                .orderId(orderId)
                .eventType(type)
                .reason(reason)
                .note(note)
                .actorUsername(username)
                .createdAt(LocalDateTime.now(clock))
                .build());
    }

    /**
     * {@link #record} plus where the phone was: an invalid / missing position is stored as unknown. The distance is a
     * straight line to the centre of the customer's subdistrict (orders have no exact coordinates), so a few km is
     * normal in rural areas.
     */
    private void recordAt(Long jobId, Order order, String type, String reason, String note, GeoPoint location,
                          String username) {
        DeliveryJobEvent.DeliveryJobEventBuilder event = DeliveryJobEvent.builder()
                .jobId(jobId)
                .orderId(order.getOrderId())
                .eventType(type)
                .reason(reason)
                .note(note)
                .actorUsername(username)
                .createdAt(LocalDateTime.now(clock));
        if (location != null && location.isValid()) {
            event.lat(BigDecimal.valueOf(location.getLat()).setScale(6, java.math.RoundingMode.HALF_UP))
                    .lng(BigDecimal.valueOf(location.getLng()).setScale(6, java.math.RoundingMode.HALF_UP))
                    .accuracyM(location.getAccuracyM() == null || location.getAccuracyM() < 0 ? null
                            : (int) Math.min(Math.round(location.getAccuracyM()), 1_000_000))
                    .distanceKm(distanceToCustomerArea(order, location));
        }
        deliveryJobEventRepository.save(event.build());
    }

    private BigDecimal distanceToCustomerArea(Order order, GeoPoint location) {
        String code = order.getAddress() == null ? null : order.getAddress().getSubdistrictCode();
        if (code == null) {
            return null;
        }
        return subdistrictRepository.findByCode(code)
                .map(sub -> GeoDistance.haversineKm(BigDecimal.valueOf(location.getLat()),
                        BigDecimal.valueOf(location.getLng()), sub.getLat(), sub.getLng()))
                .orElse(null);
    }

    private record Reason(String code, String note) {
    }

    /** เหตุผลต้องอยู่ใน {@code allowed} · "อื่นๆ" ต้องพิมพ์รายละเอียด · รายละเอียดไม่เกิน {@link StopReason#NOTE_MAX} */
    private static Reason requireReason(StopActionRequest body, Map<String, String> allowed) {
        String code = body == null || !StringUtils.hasText(body.getReason()) ? null : body.getReason().trim();
        if (code == null) {
            throw new IllegalArgumentException("กรุณาเลือกเหตุผล");
        }
        if (!allowed.containsKey(code)) {
            throw new IllegalArgumentException("เหตุผลไม่ถูกต้อง");
        }
        String note = body.getNote() == null || body.getNote().isBlank() ? null : body.getNote().trim();
        if (StopReason.OTHER.equals(code) && note == null) {
            throw new IllegalArgumentException("กรุณาพิมพ์รายละเอียดของเหตุผล \"อื่นๆ\"");
        }
        if (note != null && note.length() > StopReason.NOTE_MAX) {
            throw new IllegalArgumentException("รายละเอียดยาวเกิน " + StopReason.NOTE_MAX + " ตัวอักษร");
        }
        return new Reason(code, note);
    }

    /** จุดที่ยังต้องไปส่ง — ไม่ใช่ ส่งแล้ว / ข้าม / ยกเลิก / แจ้งยกเลิก */
    private static boolean isOpen(DeliveryRouting routing) {
        return !HANDLED_STOP_STATUSES.contains(upper(routing.getRoutingStatus()));
    }

    private static String customerName(Order order) {
        return order == null || order.getCustomer() == null ? null : order.getCustomer().getName();
    }

    private JobResponse finishJobIfAllDone(DeliveryJob job, String username) {
        List<DeliveryRouting> allRoutings = deliveryRoutingRepository.findByDeliveryJobJobIdOrderBySequenceNoAsc(job.getJobId());
        if (finishIfAllDone(job, allRoutings, username)) {
            deliveryJobRepository.save(job);
        }
        return mapToResponse(job, allRoutings, username);
    }

    /**
     * งานปิดเองเมื่อทุกจุด "ส่งแล้ว" หรือ "ยกเลิก" เท่านั้น — มีจุดที่ข้าม / แจ้งยกเลิกค้างอยู่ งานยังเปิด
     * ให้คนส่งกลับไปส่ง หรือกด "ปิดงาน" เอง (risk ข้อ 3) · คืน true ถ้างานถูกปิด (ผู้เรียกต้อง save job เอง)
     */
    private boolean finishIfAllDone(DeliveryJob job, List<DeliveryRouting> routings, String username) {
        boolean allDone = !routings.isEmpty() && routings.stream()
                .allMatch(r -> FINAL_STOP_STATUSES.contains(upper(r.getRoutingStatus())));
        if (!allDone) {
            return false;
        }
        closeAndReleaseSkipped(job, routings, username);
        record(job.getJobId(), null, JobEventType.JOB_FINISHED, null, null, username);
        return true;
    }

    /**
     * ปิดงาน — order ของจุดที่ข้ามคืนเป็น PENDING เพื่อจัดเข้างานรอบถัดไปได้
     * ยกเว้น order ที่ถูกข้ามครบ {@link #MAX_SKIPS_BEFORE_FAILED} ครั้ง (รวมงานนี้) เป็น FAILED
     * ผู้เรียกต้อง save job เอง
     */
    private void closeAndReleaseSkipped(DeliveryJob job, List<DeliveryRouting> routings, String username) {
        List<DeliveryRouting> skipped = routings.stream()
                .filter(r -> STOP_SKIPPED.equalsIgnoreCase(r.getRoutingStatus()))
                .toList();
        Map<Long, Long> skipCounts = skipped.isEmpty() ? Map.of() : countSkipsByOrder(skipped, job.getJobId());
        for (DeliveryRouting r : skipped) {
            Order order = r.getOrder();
            long skips = skipCounts.getOrDefault(order.getOrderId(), 0L);
            order.setOrderStatus(skips >= MAX_SKIPS_BEFORE_FAILED ? OrderStatus.FAILED : OrderStatus.PENDING);
            order.setUpdatedBy(username);
            orderRepository.save(order);
        }
        job.setDeliveryStatus("COMPLETED");
        job.setCompletedDate(LocalDateTime.now());
        job.setUpdatedBy(username);
    }

    private Map<Long, Long> countSkipsByOrder(List<DeliveryRouting> skipped, Long jobId) {
        List<Long> orderIds = skipped.stream().map(r -> r.getOrder().getOrderId()).toList();
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : deliveryRoutingRepository.countSkipsByOrder(orderIds, jobId)) {
            counts.put((Long) row[0], ((Number) row[1]).longValue());
        }
        return counts;
    }

    private static String upper(String value) {
        return value == null ? "" : value.toUpperCase();
    }

    @Transactional(readOnly = true)
    public List<JobResponse> listJobs(String username) {
        // งานที่ฉันสร้าง + งานที่ถูก assign ให้ฉันจากทุกทีมที่ฉันยัง ACTIVE (ทุกสถานะ รวมที่ปิดแล้ว — design §10 Q1)
        Map<Long, DeliveryJob> byId = new java.util.LinkedHashMap<>();
        Stream.concat(deliveryJobRepository.findByStatusAndCreatedByOrderByCreatedAtDesc("A", username).stream(),
                        jobAccessService.findAssignedJobs(username).stream())
                .forEach(job -> byId.putIfAbsent(job.getJobId(), job));
        if (byId.isEmpty()) {
            return List.of();
        }
        List<DeliveryJob> jobs = byId.values().stream()
                .sorted(Comparator.comparing(DeliveryJob::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        Map<Long, JobAccessService.JobPeople> people = jobAccessService.describe(jobs);
        Map<Long, JobDriverRequestService.JobRequests> requests = jobDriverRequestService.describe(
                jobs.stream().filter(job -> isOwner(job.getCreatedBy(), username)).toList());

        List<Long> jobIds = jobs.stream().map(DeliveryJob::getJobId).toList();
        Map<Long, List<DeliveryRouting>> routingsByJobId = deliveryRoutingRepository.findByDeliveryJobJobIdIn(jobIds)
                .stream()
                .collect(Collectors.groupingBy(r -> r.getDeliveryJob().getJobId()));

        return jobs.stream()
                .map(job -> mapToResponse(job, routingsByJobId.getOrDefault(job.getJobId(), List.of()), username,
                        people.get(job.getJobId()), requests.get(job.getJobId())))
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
                                                             String filterSubdistrictCode,
                                                             String username) {
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

        List<Order> candidates = orderRepository.findCandidateOrdersForJob(
                provinceCodes, districtFilter, subdistrictFilter, username, PageRequest.of(0, MAX_CANDIDATE_ORDERS));
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
    public RoutePreviewResponse previewRoute(RoutePreviewRequest request, String username) {
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
            if (order == null || !isOwner(order.getCreatedBy(), username)) {
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
                .appointmentDate(order.getAppointmentDate())
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

    private JobResponse.RoutingItem toRoutingItem(DeliveryRouting r, boolean showPrice, boolean hideContact) {
        OrderResponse.OrderAddressResponse address = mapToAddressResponse(r.getOrder().getAddress());
        if (hideContact && address != null) {
            address.setAddressLine(null);
        }
        return JobResponse.RoutingItem.builder()
                .orderId(r.getOrder().getOrderId())
                .orderCode(r.getOrder().getOrderCode())
                .customerName(r.getOrder().getCustomer() != null ? r.getOrder().getCustomer().getName() : null)
                .address(address)
                .sequenceNo(r.getSequenceNo())
                .routingStatus(r.getRoutingStatus())
                .statusReason(r.getStatusReason())
                .statusReasonText(StopReason.text(r.getStatusReason(), r.getStatusNote()))
                .orderStatus(r.getOrder().getOrderStatus())
                .tel(hideContact || r.getOrder().getCustomer() == null ? null : r.getOrder().getCustomer().getPhone())
                .detail(hideContact ? null : r.getOrder().getDetail())
                .deliveredAt(r.getOrder().getDeliveryDate())
                .price(showPrice ? r.getOrder().getPrice() : null)
                .build();
    }

    private JobResponse mapToResponse(DeliveryJob job, List<DeliveryRouting> routings, String username) {
        // คำขอรับงาน: เจ้าของเท่านั้นที่เห็น — คนส่งไม่ต้อง query
        JobDriverRequestService.JobRequests requests = isOwner(job.getCreatedBy(), username)
                ? jobDriverRequestService.describe(List.of(job)).get(job.getJobId()) : null;
        return mapToResponse(job, routings, username, jobAccessService.describe(List.of(job)).get(job.getJobId()),
                requests);
    }

    /** The driver as shown on the job; only the owner gets the driver's phone, and only while they share a team. */
    private static TeamUserSummary assignedDriverFor(JobAccessService.JobPeople people, boolean manage) {
        if (people == null || people.assignedDriver() == null) {
            return null;
        }
        TeamUserSummary driver = people.assignedDriver();
        return TeamUserSummary.builder()
                .userId(driver.getUserId())
                .username(driver.getUsername())
                .displayName(driver.getDisplayName())
                .phone(manage && people.sameTeam() ? people.driverPhone() : null)
                .build();
    }

    /**
     * {@code people} may be null (not loaded) — the job is then shown without owner / driver names.
     * {@code requests} null = no request to show (always null for a driver).
     */
    private JobResponse mapToResponse(DeliveryJob job, List<DeliveryRouting> routings, String username,
                                      JobAccessService.JobPeople people, JobDriverRequestService.JobRequests requests) {
        boolean manage = isOwner(job.getCreatedBy(), username);
        // ราคา: เจ้าของเห็นเสมอ · คนส่งเห็นเมื่อเจ้าของเปิด driver.show-price (design §5.3)
        boolean showPrice = manage || (people != null && people.driverSeesPrice());
        AddressInfoDto origin = masterDataService.getAddressInfoSafe(
                job.getOriginProvinceCode(), job.getOriginDistrictCode(), job.getOriginSubdistrictCode());

        boolean jobClosed = JobStatus.COMPLETED.equalsIgnoreCase(job.getDeliveryStatus());
        List<JobResponse.RoutingItem> routingItems = routings.stream()
                .sorted(Comparator.comparing(DeliveryRouting::getSequenceNo))
                .map(r -> {
                    // คนส่ง: จุดที่ส่งแล้ว / ยกเลิกแล้ว หรืองานปิดแล้ว ไม่ต้องติดต่อลูกค้าอีก → ไม่ส่งเบอร์ บ้านเลขที่
                    // และรายละเอียดสินค้า (driver-risk ข้อ 13 — กันจดไว้ติดต่อลูกค้าเอง) · ชื่อ + ตำบลยังเห็นเพื่ออ้างอิง
                    boolean hideContact = !manage
                            && (jobClosed || FINAL_STOP_STATUSES.contains(upper(r.getRoutingStatus())));
                    return toRoutingItem(r, showPrice, hideContact);
                })
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
                .ownerUsername(job.getCreatedBy())
                .ownerDisplayName(people != null && people.owner() != null ? people.owner().getDisplayName() : null)
                .assignedDriver(assignedDriverFor(people, manage))
                .ownerPhone(!manage && people != null && people.sameTeam() ? people.ownerPhone() : null)
                .myAccess(manage ? JobAccess.MANAGE : JobAccess.OPERATE)
                .driverRequest(manage && requests != null ? requests.pending() : null)
                .lastDeclined(manage && requests != null ? requests.lastDeclined() : null)
                .declinedDriverIds(manage && requests != null ? requests.declinedDriverIds() : null)
                .routings(routingItems)
                .build();
    }
}
