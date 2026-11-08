package com.core.service.admin;

import com.authen.entity.User;
import com.authen.repository.UserRepository;
import com.authen.repository.UserSessionRepository;
import com.common.exception.PermissionDeniedException;
import com.core.constant.AccountRole;
import com.core.dto.admin.AdminJobResponse;
import com.core.dto.admin.AdminOrderResponse;
import com.core.dto.admin.AdminUserResponse;
import com.core.entity.DeliveryJob;
import com.core.entity.DeliveryRouting;
import com.core.entity.Order;
import com.core.repository.DeliveryJobRepository;
import com.core.repository.DeliveryRoutingRepository;
import com.core.repository.JobDriverRequestRepository;
import com.core.repository.OrderRepository;
import com.core.repository.TeamMemberRepository;
import com.core.service.JobService;
import com.core.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * เมนูผู้ดูแลระบบ (docs/user-stories/admin.md) — ทุก method ตรวจก่อนว่าผู้เรียกเป็นบัญชี ADMIN ที่ยังใช้งานได้
 * (ตาราง permissions กันไว้ชั้นหนึ่งแล้ว แต่ profile ที่ปิด security filter ไม่ตรวจ — ที่นี่จึงตรวจซ้ำเสมอ)
 * <ul>
 *   <li>ผู้ใช้: ดูรายการ / ปิด / เปิด / ลบ (ซ่อน) / ตั้งรหัสผ่านใหม่ — ทำกับบัญชี ADMIN ไม่ได้ (กันล็อกตัวเองออก)</li>
 *   <li>ออเดอร์ / งาน: ดูรายการ / ลบ / ย้อนเป็นขั้นแรก — กฎอยู่ใน {@link OrderService} / {@link JobService}</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    static final int MAX_USERS = 200;
    static final int MAX_ROWS = 100;
    static final String ADMIN_ONLY = "สำหรับผู้ดูแลระบบเท่านั้น";
    static final String USER_NOT_FOUND = "ไม่พบผู้ใช้นี้";
    static final String ADMIN_ACCOUNT = "บัญชีผู้ดูแลระบบ แก้จากเมนูนี้ไม่ได้";

    private final UserRepository userRepository;
    private final UserSessionRepository userSessionRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final JobDriverRequestRepository jobDriverRequestRepository;
    private final DeliveryJobRepository deliveryJobRepository;
    private final DeliveryRoutingRepository deliveryRoutingRepository;
    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final JobService jobService;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    // ---- users ----

    @Transactional(readOnly = true)
    public List<AdminUserResponse> listUsers(String adminUsername, String query) {
        requireAdmin(adminUsername);
        return userRepository.searchForAdmin(pattern(query), PageRequest.of(0, MAX_USERS)).stream()
                .map(AdminService::toUserResponse)
                .toList();
    }

    /** ปิดการใช้งาน — login ไม่ได้ และ session ที่เปิดอยู่จบทันที (งานที่ค้างไม่ถูกแตะ เปิดกลับมาได้) */
    @Transactional
    public AdminUserResponse disableUser(String adminUsername, Integer userId) {
        User admin = requireAdmin(adminUsername);
        User user = findManageableUser(userId);
        user.setIsActive(false);
        userRepository.save(user);
        userSessionRepository.deleteByUserId(userId);
        log.info("admin {} disabled user {}", admin.getUsername(), user.getUsername());
        return toUserResponse(user);
    }

    @Transactional
    public AdminUserResponse enableUser(String adminUsername, Integer userId) {
        User admin = requireAdmin(adminUsername);
        User user = findManageableUser(userId);
        user.setIsActive(true);
        userRepository.save(user);
        log.info("admin {} enabled user {}", admin.getUsername(), user.getUsername());
        return toUserResponse(user);
    }

    /**
     * ลบผู้ใช้ = ลบแบบซ่อน (deleted_at) เพราะแถว users ถูกอ้างจากหลายตาราง: ปิดบัญชี + จบ session +
     * จบทีมที่เปิดอยู่ (เชิญ → CANCELLED, สมาชิก → REMOVED) + ยกเลิกคำขอรับงานที่รอตอบ
     * ไม่ได้ถ้ายังมีงานที่ยังไม่ปิด (เป็นเจ้าของ หรือเป็นคนส่งที่รับงานไว้) — ให้ลบ / ปิดงานก่อน
     */
    @Transactional
    public void deleteUser(String adminUsername, Integer userId) {
        User admin = requireAdmin(adminUsername);
        User user = findManageableUser(userId);
        long openJobs = deliveryJobRepository.countOpenOfUser(user.getUsername(), user.getUserId());
        if (openJobs > 0) {
            throw new IllegalArgumentException("ลบไม่ได้ ผู้ใช้นี้ยังมีงานที่ยังไม่ปิด " + openJobs
                    + " งาน — ลบหรือปิดงานก่อน");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        user.setIsActive(false);
        user.setDeletedAt(now);
        userRepository.save(user);
        userSessionRepository.deleteByUserId(userId);
        teamMemberRepository.endAllOfUser(userId, now, admin.getUsername());
        jobDriverRequestRepository.cancelPendingOfUser(userId, now, admin.getUsername());
        log.info("admin {} deleted user {}", admin.getUsername(), user.getUsername());
    }

    /** ตั้งรหัสผ่านใหม่ (เข้ารหัสแบบเดียวกับตอน login) แล้วจบ session ทุกเครื่อง ให้ผู้ใช้เข้าด้วยรหัสใหม่ */
    @Transactional
    public void changePassword(String adminUsername, Integer userId, String newPassword) {
        User admin = requireAdmin(adminUsername);
        User user = findManageableUser(userId);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        userSessionRepository.deleteByUserId(userId);
        log.info("admin {} set a new password for user {}", admin.getUsername(), user.getUsername());
    }

    // ---- orders ----

    @Transactional(readOnly = true)
    public List<AdminOrderResponse> listOrders(String adminUsername, String query) {
        requireAdmin(adminUsername);
        List<Order> orders = orderRepository.searchForAdmin(pattern(query), PageRequest.of(0, MAX_ROWS));
        if (orders.isEmpty()) {
            return List.of();
        }
        // งานล่าสุด (ที่ยังไม่ถูกลบ) ของแต่ละออเดอร์
        Map<Long, DeliveryJob> latestJob = deliveryRoutingRepository
                .findByOrderOrderIdIn(orders.stream().map(Order::getOrderId).toList()).stream()
                .filter(r -> "A".equals(r.getDeliveryJob().getStatus()))
                .collect(Collectors.toMap(r -> r.getOrder().getOrderId(), Function.identity(),
                        (a, b) -> a.getRouteId() > b.getRouteId() ? a : b))
                .entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getDeliveryJob()));
        return orders.stream().map(o -> toOrderResponse(o, latestJob.get(o.getOrderId()))).toList();
    }

    @Transactional
    public void resetOrder(String adminUsername, Long orderId) {
        requireAdmin(adminUsername);
        orderService.resetOrderAsAdmin(orderId, adminUsername);
    }

    @Transactional
    public void deleteOrder(String adminUsername, Long orderId) {
        requireAdmin(adminUsername);
        orderService.deleteOrderAsAdmin(orderId, adminUsername);
    }

    // ---- jobs ----

    @Transactional(readOnly = true)
    public List<AdminJobResponse> listJobs(String adminUsername, String query) {
        requireAdmin(adminUsername);
        List<DeliveryJob> jobs = deliveryJobRepository.searchForAdmin(pattern(query), PageRequest.of(0, MAX_ROWS));
        if (jobs.isEmpty()) {
            return List.of();
        }
        List<Integer> driverIds = jobs.stream().map(DeliveryJob::getAssignedDriverId).filter(Objects::nonNull)
                .distinct().toList();
        Map<Integer, String> drivers = driverIds.isEmpty() ? Map.of() : userRepository.findAllById(driverIds).stream()
                .collect(Collectors.toMap(User::getUserId, User::getUsername));
        Map<Long, List<DeliveryRouting>> stops = deliveryRoutingRepository
                .findByDeliveryJobJobIdIn(jobs.stream().map(DeliveryJob::getJobId).toList()).stream()
                .collect(Collectors.groupingBy(r -> r.getDeliveryJob().getJobId()));
        return jobs.stream()
                .map(j -> toJobResponse(j, stops.getOrDefault(j.getJobId(), List.of()), drivers))
                .toList();
    }

    @Transactional
    public void resetJob(String adminUsername, Long jobId) {
        requireAdmin(adminUsername);
        jobService.resetJobAsAdmin(jobId, adminUsername);
    }

    @Transactional
    public void deleteJob(String adminUsername, Long jobId) {
        requireAdmin(adminUsername);
        jobService.deleteJobAsAdmin(jobId, adminUsername);
    }

    // ---- helpers ----

    /** ผู้เรียกต้องเป็นบัญชี ADMIN ที่ยังใช้งานได้ ไม่งั้น 403 */
    User requireAdmin(String username) {
        User user = username == null ? null : userRepository.findByUsername(username).orElse(null);
        if (user == null || !isAdmin(user) || !Boolean.TRUE.equals(user.getIsActive()) || user.getDeletedAt() != null) {
            throw new PermissionDeniedException(ADMIN_ONLY);
        }
        return user;
    }

    /** ผู้ใช้ที่ยังไม่ถูกลบ และไม่ใช่บัญชี ADMIN */
    private User findManageableUser(Integer userId) {
        User user = userRepository.findById(userId)
                .filter(u -> u.getDeletedAt() == null)
                .orElseThrow(() -> new IllegalArgumentException(USER_NOT_FOUND));
        if (isAdmin(user)) {
            throw new IllegalArgumentException(ADMIN_ACCOUNT);
        }
        return user;
    }

    private static boolean isAdmin(User user) {
        return user.getRole() != null && AccountRole.ADMIN.equals(user.getRole().getRoleName());
    }

    /** "%text%" ตัวเล็ก · ว่าง = ทั้งหมด · ตัด % _ ที่พิมพ์มาออก (ไม่ให้เป็น wildcard) */
    static String pattern(String query) {
        String text = query == null ? "" : query.trim().toLowerCase(Locale.ROOT).replaceAll("[%_\\\\]", "");
        return "%" + text + "%";
    }

    private static AdminUserResponse toUserResponse(User user) {
        String name = (Objects.toString(user.getFirstName(), "") + " " + Objects.toString(user.getLastName(), "")).trim();
        return AdminUserResponse.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .displayName(name.isEmpty() ? user.getUsername() : name)
                .role(user.getRole() == null ? null : user.getRole().getRoleName())
                .active(Boolean.TRUE.equals(user.getIsActive()))
                .lastLogin(user.getLastLogin())
                .lastSeenAt(user.getLastSeenAt())
                .createdAt(user.getCreatedAt())
                .build();
    }

    private static AdminOrderResponse toOrderResponse(Order order, DeliveryJob job) {
        return AdminOrderResponse.builder()
                .orderId(order.getOrderId())
                .orderCode(order.getOrderCode())
                .owner(order.getCreatedBy())
                .customerName(order.getCustomer() == null ? null : order.getCustomer().getName())
                .commodity(order.getCommodity())
                .orderStatus(order.getOrderStatus())
                .appointmentDate(order.getAppointmentDate())
                .createdAt(order.getCreatedAt())
                .jobCode(job == null ? null : job.getJobCode())
                .jobDeliveryStatus(job == null ? null : job.getDeliveryStatus())
                .build();
    }

    private static AdminJobResponse toJobResponse(DeliveryJob job, List<DeliveryRouting> stops,
                                                  Map<Integer, String> drivers) {
        int completed = (int) stops.stream().filter(r -> "COMPLETED".equalsIgnoreCase(r.getRoutingStatus())).count();
        return AdminJobResponse.builder()
                .jobId(job.getJobId())
                .jobCode(job.getJobCode())
                .jobName(job.getJobName())
                .owner(job.getCreatedBy())
                .deliveryStatus(job.getDeliveryStatus())
                .scheduledDate(job.getScheduledDate())
                .stopCount(stops.size())
                .completedStopCount(completed)
                .driverUsername(job.getAssignedDriverId() == null ? null : drivers.get(job.getAssignedDriverId()))
                .createdAt(job.getCreatedAt())
                .build();
    }
}
