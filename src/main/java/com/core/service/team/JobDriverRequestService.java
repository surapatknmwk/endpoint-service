package com.core.service.team;

import com.authen.entity.User;
import com.authen.repository.UserRepository;
import com.common.exception.PermissionDeniedException;
import com.core.constant.DeclineReason;
import com.core.constant.JobDriverRequestStatus;
import com.core.constant.JobEventType;
import com.core.entity.DeliveryJobEvent;
import com.core.repository.DeliveryJobEventRepository;
import com.core.constant.JobStatus;
import com.core.constant.TeamMemberStatus;
import com.core.dto.DeclineRequest;
import com.core.dto.DriverRequestResponse;
import com.core.dto.JobResponse;
import com.core.entity.DeliveryJob;
import com.core.entity.DeliveryRouting;
import com.core.entity.JobDriverRequest;
import com.core.entity.OrderAddress;
import com.core.dto.master.AddressInfoDto;
import com.core.repository.DeliveryJobRepository;
import com.core.repository.DeliveryRoutingRepository;
import com.core.repository.JobDriverRequestRepository;
import com.core.repository.TeamMemberRepository;
import com.core.service.clients.MasterDataService;
import com.core.service.notification.TeamNotifier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Choosing a driver asks them first (design docs/task/enhanment/assignConfirm/design.md): the owner's choice creates
 * a PENDING request, the driver accepts (→ {@code delivery_jobs.assigned_driver_id}) or declines, and the owner is
 * told either way. Who may operate a job is unchanged — it still follows {@code assigned_driver_id} only.
 *
 * <p>Locks: the job row first, then its pending request — the same order for the owner and the driver, so they
 * cannot deadlock and one waits for the other.</p>
 */
@Service
@RequiredArgsConstructor
public class JobDriverRequestService {

    static final String JOB_STARTED = "เปลี่ยนคนส่งได้เฉพาะงานที่ยังไม่เริ่ม";
    static final String REQUEST_BUSY = "มีคำขออื่นของงานนี้อยู่ ลองใหม่อีกครั้ง";
    static final String REQUEST_NOT_FOUND = "ไม่พบคำขอ";
    static final String REQUEST_CLOSED = "คำขอนี้ไม่สามารถตอบได้แล้ว";
    static final String INVALID_REASON = "เหตุผลไม่ถูกต้อง";
    static final String NOTE_ONLY_OTHER = "พิมพ์รายละเอียดได้เฉพาะเหตุผล \"อื่นๆ\"";
    static final String DRIVER_ONLY = "ไม่มีสิทธิ์ทำรายการนี้";
    static final String REQUEST_EXPIRED = "คำขอนี้หมดอายุแล้ว";
    static final String RELEASE_STARTED = "คืนงานได้เฉพาะงานที่ยังไม่เริ่ม";
    static final String JOB_NOT_FOUND = "Job not found: ";

    private final JobDriverRequestRepository requestRepository;
    private final DeliveryJobRepository deliveryJobRepository;
    private final DeliveryRoutingRepository deliveryRoutingRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final UserRepository userRepository;
    private final JobAccessService jobAccessService;
    private final TeamNotifier teamNotifier;
    private final MasterDataService masterDataService;
    private final Clock clock;
    private final JobRequestPolicy jobRequestPolicy;
    private final DeliveryJobEventRepository deliveryJobEventRepository;

    /** What the owner sees about requests on one job. Every field may be null / empty. */
    public record JobRequests(JobResponse.DriverRequestInfo pending, JobResponse.DriverRequestInfo lastDeclined,
                              List<Integer> declinedDriverIds) {
    }

    // ---- owner ----

    /**
     * The owner picks a driver for a job they already hold locked ({@code PATCH /jobs/{jobId}/driver}).
     * <ul>
     *   <li>the driver who accepted, or the one already asked → nothing changes;</li>
     *   <li>{@code null} → the accepted driver is unassigned and / or the pending request cancelled (G10);</li>
     *   <li>anyone else → the accepted driver is unassigned (G1), a pending request cancelled (G2), and the new
     *   driver asked.</li>
     * </ul>
     * Only while the job is NEW (G3) and only an ACTIVE member of the owner's team.
     */
    public void requestDriver(DeliveryJob job, Integer driverUserId, String ownerUsername, int stopCount) {
        Optional<JobDriverRequest> pending = requestRepository.findPendingForUpdate(job.getJobId());
        Integer askedDriver = pending.map(JobDriverRequest::getDriverUserId).orElse(null);
        if (driverUserId != null
                && (driverUserId.equals(job.getAssignedDriverId()) || driverUserId.equals(askedDriver))) {
            return;
        }
        if (driverUserId == null && job.getAssignedDriverId() == null && pending.isEmpty()) {
            return;
        }
        if (!JobStatus.NEW.equalsIgnoreCase(job.getDeliveryStatus())) {
            throw new IllegalArgumentException(JOB_STARTED);
        }
        User driver = driverUserId == null ? null : jobAccessService.lockActiveMember(ownerUsername, driverUserId);
        LocalDateTime now = LocalDateTime.now(clock);

        pending.ifPresent(request -> cancel(request, job, ownerUsername, now));
        if (job.getAssignedDriverId() != null) {
            Integer previousDriverId = job.getAssignedDriverId();
            job.setAssignedDriverId(null);
            jobAccessService.notifyUnassigned(job, previousDriverId);
        }
        job.setUpdatedBy(ownerUsername);
        deliveryJobRepository.save(job);

        if (driver != null) {
            User owner = userRepository.findByUsername(ownerUsername)
                    .orElseThrow(() -> new IllegalArgumentException(JobAccessService.NOT_A_MEMBER));
            if (requestRepository.insertPending(job.getJobId(), owner.getUserId(), driver.getUserId(), now,
                    ownerUsername) == 0) {
                throw new IllegalArgumentException(REQUEST_BUSY);
            }
            teamNotifier.jobRequested(job, owner, driver, stopCount);
        }
    }

    /** The owner started or deleted the job itself (G4): a waiting driver is told the request is gone. */
    public void cancelPending(DeliveryJob job, String by) {
        requestRepository.findPendingForUpdate(job.getJobId())
                .ifPresent(request -> cancel(request, job, by, LocalDateTime.now(clock)));
    }

    /**
     * Pending request, latest decline (only while the job has no driver) and who declined, per job — one query per
     * kind for a whole list. Owners only; callers skip drivers.
     */
    @Transactional(readOnly = true)
    public Map<Long, JobRequests> describe(Collection<DeliveryJob> jobs) {
        if (jobs.isEmpty()) {
            return Map.of();
        }
        List<Long> jobIds = jobs.stream().map(DeliveryJob::getJobId).toList();
        Map<Long, JobDriverRequest> latest = requestRepository.findLatestByJobIds(jobIds).stream()
                .collect(Collectors.toMap(JobDriverRequest::getJobId, Function.identity()));
        Map<Long, List<Integer>> declined = requestRepository.findDeclinedDrivers(jobIds).stream()
                .collect(Collectors.groupingBy(JobDriverRequestRepository.JobDriver::getJobId,
                        Collectors.mapping(JobDriverRequestRepository.JobDriver::getDriverUserId,
                                Collectors.toList())));
        if (latest.isEmpty() && declined.isEmpty()) {
            return Map.of();
        }
        Map<Integer, User> drivers = usersById(latest.values().stream().map(JobDriverRequest::getDriverUserId)
                .collect(Collectors.toSet()));

        Map<Long, JobRequests> result = new HashMap<>();
        for (DeliveryJob job : jobs) {
            JobDriverRequest request = latest.get(job.getJobId());
            JobResponse.DriverRequestInfo pending = null;
            JobResponse.DriverRequestInfo lastDeclined = null;
            if (request != null && JobDriverRequestStatus.PENDING.equals(request.getRequestStatus())) {
                pending = toInfo(request, drivers.get(request.getDriverUserId()));
                pending.setExpiresAt(jobRequestPolicy.expiresAt(request, job));
            } else if (request != null && NO_DRIVER_ANSWERS.contains(request.getRequestStatus())
                    && job.getAssignedDriverId() == null) {
                // ปฏิเสธ / ไม่ตอบภายในเวลา / คืนงาน — เจ้าของต้องเลือกคนใหม่
                lastDeclined = toInfo(request, drivers.get(request.getDriverUserId()));
            }
            List<Integer> declinedIds = declined.getOrDefault(job.getJobId(), List.of());
            if (pending != null || lastDeclined != null || !declinedIds.isEmpty()) {
                result.put(job.getJobId(), new JobRequests(pending, lastDeclined, declinedIds));
            }
        }
        return result;
    }

    private static final Set<String> NO_DRIVER_ANSWERS = Set.of(JobDriverRequestStatus.DECLINED,
            JobDriverRequestStatus.EXPIRED, JobDriverRequestStatus.RELEASED);

    // ---- system ----

    /**
     * PENDING → EXPIRED for every request past {@link JobRequestPolicy#expiresAt} (driver-risk ข้อ 16) — the owner is
     * told once per request. Each one under the same locks as an answer (job, then request), so a driver accepting
     * at that moment either wins or gets "คำขอนี้หมดอายุแล้ว". Returns how many expired.
     */
    @Transactional
    public int expireOverdue() {
        LocalDateTime now = LocalDateTime.now(clock);
        int expired = 0;
        for (JobDriverRequest candidate : requestRepository.findByRequestStatus(JobDriverRequestStatus.PENDING)) {
            DeliveryJob job = deliveryJobRepository.findForUpdate(candidate.getJobId()).orElse(null);
            JobDriverRequest request = requestRepository.findForUpdate(candidate.getRequestId()).orElse(null);
            if (request == null || !JobDriverRequestStatus.PENDING.equals(request.getRequestStatus())
                    || !jobRequestPolicy.isExpired(request, job, now)) {
                continue;
            }
            request.setRequestStatus(JobDriverRequestStatus.EXPIRED);
            touch(request, "system", now);
            requestRepository.save(request);
            expired++;
            if (job != null && "A".equals(job.getStatus())) {
                teamNotifier.jobRequestExpired(job, new TeamNotifier.JobDriverRequestKey(request.getRequestId()),
                        userRepository.findById(request.getOwnerUserId()).orElse(null),
                        userRepository.findById(request.getDriverUserId()).orElse(null));
            }
        }
        return expired;
    }

    // ---- driver ----

    /**
     * "คืนงาน" — the driver gives back a job they accepted, before it starts (driver-risk ข้อ 16). Optional reason as
     * for declining. The job loses its driver, the accepted request becomes RELEASED, the owner is told and the job
     * history gets JOB_RELEASED. Not my job / not ACTIVE in the owner's team → "Job not found" like any other job.
     */
    @Transactional
    public void release(String username, Long jobId, DeclineRequest body) {
        User me = requireDriver(username);
        String reason = body == null ? null : trimToNull(body.getReason());
        String note = body == null ? null : trimToNull(body.getNote());
        if (reason != null && !DeclineReason.LABELS.containsKey(reason)) {
            throw new IllegalArgumentException(INVALID_REASON);
        }
        if (note != null && !DeclineReason.OTHER.equals(reason)) {
            throw new IllegalArgumentException(NOTE_ONLY_OTHER);
        }
        DeliveryJob job = deliveryJobRepository.findForUpdate(jobId)
                .filter(j -> "A".equals(j.getStatus()) && jobAccessService.isAssignedActiveDriver(j, username))
                .orElseThrow(() -> new IllegalArgumentException(JOB_NOT_FOUND + jobId));
        if (!JobStatus.NEW.equalsIgnoreCase(job.getDeliveryStatus())) {
            throw new IllegalArgumentException(RELEASE_STARTED);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        requestRepository.findFirstByJobIdAndDriverUserIdAndRequestStatusOrderByRequestIdDesc(jobId, me.getUserId(),
                JobDriverRequestStatus.ACCEPTED).ifPresent(accepted -> {
            accepted.setRequestStatus(JobDriverRequestStatus.RELEASED);
            accepted.setDeclineReason(reason);
            accepted.setDeclineNote(note);
            accepted.setRespondedAt(now);
            touch(accepted, username, now);
            requestRepository.save(accepted);
        });
        job.setAssignedDriverId(null);
        job.setUpdatedBy(username);
        deliveryJobRepository.save(job);
        deliveryJobEventRepository.save(DeliveryJobEvent.builder()
                .jobId(jobId).eventType(JobEventType.JOB_RELEASED).reason(reason).note(note)
                .actorUsername(username).createdAt(now).build());
        teamNotifier.jobReleased(job, userRepository.findByUsername(job.getCreatedBy()).orElse(null), me,
                DeclineReason.text(reason, note));
    }

    /**
     * My pending requests, newest first, from teams I am still ACTIVE in — a summary without customer data (F1).
     */
    @Transactional(readOnly = true)
    public List<DriverRequestResponse> listMyRequests(String username) {
        User me = requireDriver(username);
        List<JobDriverRequest> rows = requestRepository.findByDriverUserIdAndRequestStatusOrderByRequestedAtDesc(
                me.getUserId(), JobDriverRequestStatus.PENDING);
        if (rows.isEmpty()) {
            return List.of();
        }
        Set<Integer> ownerIds = rows.stream().map(JobDriverRequest::getOwnerUserId).collect(Collectors.toSet());
        Set<Integer> activeOwners = teamMemberRepository.findByOwnerUserIdInAndMemberUserIdInAndMemberStatusIn(
                        ownerIds, List.of(me.getUserId()), List.of(TeamMemberStatus.ACTIVE)).stream()
                .map(row -> row.getOwnerUserId())
                .collect(Collectors.toSet());
        List<JobDriverRequest> visible = rows.stream().filter(r -> activeOwners.contains(r.getOwnerUserId())).toList();
        if (visible.isEmpty()) {
            return List.of();
        }
        List<Long> jobIds = visible.stream().map(JobDriverRequest::getJobId).toList();
        Map<Long, DeliveryJob> jobs = deliveryJobRepository.findAllById(jobIds).stream()
                .collect(Collectors.toMap(DeliveryJob::getJobId, Function.identity()));
        Map<Long, List<DeliveryRouting>> stops = deliveryRoutingRepository.findByDeliveryJobJobIdIn(jobIds).stream()
                .collect(Collectors.groupingBy(r -> r.getDeliveryJob().getJobId()));
        Map<Integer, User> owners = usersById(ownerIds);
        Map<String, String> areaNames = new HashMap<>();
        Map<LocalDate, int[]> busyDays = myOpenJobsByDay(me);
        LocalDateTime now = LocalDateTime.now(clock);

        return visible.stream()
                .filter(r -> {
                    DeliveryJob job = jobs.get(r.getJobId());
                    // หมดเวลาแล้วแต่ตัวตั้งเวลายังไม่รัน — ไม่แสดง (รับไม่ได้แล้ว)
                    return job != null && "A".equals(job.getStatus())
                            && JobStatus.NEW.equalsIgnoreCase(job.getDeliveryStatus())
                            && !jobRequestPolicy.isExpired(r, job, now);
                })
                .map(r -> {
                    DeliveryJob job = jobs.get(r.getJobId());
                    DriverRequestResponse summary = toSummary(r, job, stops.getOrDefault(r.getJobId(), List.of()),
                            owners.get(r.getOwnerUserId()), areaNames);
                    int[] busy = job.getScheduledDate() == null ? null
                            : busyDays.get(job.getScheduledDate().toLocalDate());
                    if (busy != null) {
                        summary.setSameDayJobCount(busy[0]);
                        summary.setSameDayStopCount(busy[1]);
                    }
                    summary.setExpiresAt(jobRequestPolicy.expiresAt(r, job));
                    return summary;
                })
                .toList();
    }

    /**
     * PENDING → ACCEPTED: the job becomes mine ({@code assigned_driver_id}) and the owner is told. Refused when the
     * request is no longer pending, the job has started / is gone, or I am no longer ACTIVE in the owner's team.
     */
    @Transactional
    public DriverRequestResponse accept(String username, Long requestId) {
        User me = requireDriver(username);
        Long jobId = requestRepository.findJobIdOfDriverRequest(requestId, me.getUserId())
                .orElseThrow(() -> new IllegalArgumentException(REQUEST_NOT_FOUND));
        DeliveryJob job = deliveryJobRepository.findForUpdate(jobId)
                .orElseThrow(() -> new IllegalArgumentException(REQUEST_CLOSED));
        JobDriverRequest request = requestRepository.findForUpdate(requestId)
                .orElseThrow(() -> new IllegalArgumentException(REQUEST_NOT_FOUND));
        if (!JobDriverRequestStatus.PENDING.equals(request.getRequestStatus())
                || !"A".equals(job.getStatus()) || !JobStatus.NEW.equalsIgnoreCase(job.getDeliveryStatus())) {
            throw new IllegalArgumentException(REQUEST_CLOSED);
        }
        if (jobRequestPolicy.isExpired(request, job, LocalDateTime.now(clock))) {
            throw new IllegalArgumentException(REQUEST_EXPIRED);
        }
        User owner = userRepository.findById(request.getOwnerUserId())
                .filter(o -> o.getUsername().equals(job.getCreatedBy()))
                .orElseThrow(() -> new IllegalArgumentException(REQUEST_CLOSED));
        try {
            // same lock as assigning used to take: a removal from the team waits, then sees this job
            jobAccessService.lockActiveMember(owner.getUsername(), me.getUserId());
        } catch (IllegalArgumentException notMember) {
            throw new IllegalArgumentException(REQUEST_CLOSED);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        request.setRequestStatus(JobDriverRequestStatus.ACCEPTED);
        request.setRespondedAt(now);
        touch(request, me.getUsername(), now);
        requestRepository.save(request);
        job.setAssignedDriverId(me.getUserId());
        job.setUpdatedBy(me.getUsername());
        deliveryJobRepository.save(job);
        teamNotifier.jobRequestAnswered(job, owner, me, true, null);
        return toResult(request, job);
    }

    /** PENDING → DECLINED with an optional reason (F2); the owner is told who declined and why. */
    @Transactional
    public DriverRequestResponse decline(String username, Long requestId, DeclineRequest body) {
        User me = requireDriver(username);
        String reason = body == null ? null : trimToNull(body.getReason());
        String note = body == null ? null : trimToNull(body.getNote());
        if (reason != null && !DeclineReason.LABELS.containsKey(reason)) {
            throw new IllegalArgumentException(INVALID_REASON);
        }
        if (note != null && !DeclineReason.OTHER.equals(reason)) {
            throw new IllegalArgumentException(NOTE_ONLY_OTHER);
        }
        requestRepository.findJobIdOfDriverRequest(requestId, me.getUserId())
                .orElseThrow(() -> new IllegalArgumentException(REQUEST_NOT_FOUND));
        JobDriverRequest request = requestRepository.findForUpdate(requestId)
                .orElseThrow(() -> new IllegalArgumentException(REQUEST_NOT_FOUND));
        if (!JobDriverRequestStatus.PENDING.equals(request.getRequestStatus())) {
            throw new IllegalArgumentException(REQUEST_CLOSED);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        request.setRequestStatus(JobDriverRequestStatus.DECLINED);
        request.setDeclineReason(reason);
        request.setDeclineNote(note);
        request.setRespondedAt(now);
        touch(request, me.getUsername(), now);
        requestRepository.save(request);

        DeliveryJob job = deliveryJobRepository.findById(request.getJobId()).orElse(null);
        User owner = userRepository.findById(request.getOwnerUserId()).orElse(null);
        if (job != null) {
            teamNotifier.jobRequestAnswered(job, owner, me, false, DeclineReason.text(reason, note));
        }
        return toResult(request, job);
    }

    // ---- helpers ----

    /**
     * Jobs I already took (any owner whose team I am still ACTIVE in, not completed), per scheduled day:
     * [job count, stops not cancelled] — warns before taking another job the same day (driver-risk ข้อ 7).
     */
    private Map<LocalDate, int[]> myOpenJobsByDay(User me) {
        List<DeliveryJob> mine = deliveryJobRepository.findAssignedToDriver(me.getUserId()).stream()
                .filter(j -> !JobStatus.COMPLETED.equalsIgnoreCase(j.getDeliveryStatus()) && j.getScheduledDate() != null)
                .toList();
        if (mine.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> stopsByJob = deliveryRoutingRepository
                .findByDeliveryJobJobIdIn(mine.stream().map(DeliveryJob::getJobId).toList()).stream()
                .filter(r -> !"CANCELLED".equalsIgnoreCase(r.getRoutingStatus()))
                .collect(Collectors.groupingBy(r -> r.getDeliveryJob().getJobId(), Collectors.counting()));
        Map<LocalDate, int[]> byDay = new HashMap<>();
        for (DeliveryJob job : mine) {
            int[] day = byDay.computeIfAbsent(job.getScheduledDate().toLocalDate(), d -> new int[2]);
            day[0]++;
            day[1] += stopsByJob.getOrDefault(job.getJobId(), 0L).intValue();
        }
        return byDay;
    }

    private void cancel(JobDriverRequest request, DeliveryJob job, String by, LocalDateTime now) {
        request.setRequestStatus(JobDriverRequestStatus.CANCELLED);
        touch(request, by, now);
        // flushed now: the pending index must be free before a new request is inserted (native insert)
        requestRepository.saveAndFlush(request);
        userRepository.findById(request.getDriverUserId())
                .ifPresent(driver -> teamNotifier.jobRequestCancelled(job, driver));
    }

    private static void touch(JobDriverRequest request, String by, LocalDateTime now) {
        request.setUpdatedAt(now);
        request.setUpdatedBy(by);
    }

    /** Answering is the driver's side; owners have no requests addressed to them. */
    private User requireDriver(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (!TeamService.isDriver(user)) {
            throw new PermissionDeniedException(DRIVER_ONLY);
        }
        return user;
    }

    private Map<Integer, User> usersById(Collection<Integer> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getUserId, Function.identity()));
    }

    private static JobResponse.DriverRequestInfo toInfo(JobDriverRequest request, User driver) {
        boolean declined = JobDriverRequestStatus.DECLINED.equals(request.getRequestStatus())
                || JobDriverRequestStatus.RELEASED.equals(request.getRequestStatus());
        return JobResponse.DriverRequestInfo.builder()
                .requestId(request.getRequestId())
                .driver(TeamService.toSummary(driver))
                .requestStatus(request.getRequestStatus())
                .requestedAt(request.getRequestedAt())
                .declineReason(declined ? request.getDeclineReason() : null)
                .declineReasonText(declined ? DeclineReason.text(request.getDeclineReason(), request.getDeclineNote())
                        : null)
                .respondedAt(request.getRespondedAt())
                .build();
    }

    private static DriverRequestResponse toResult(JobDriverRequest request, DeliveryJob job) {
        return DriverRequestResponse.builder()
                .requestId(request.getRequestId())
                .requestStatus(request.getRequestStatus())
                .jobId(request.getJobId())
                .jobCode(job == null ? null : job.getJobCode())
                .jobName(job == null ? null : job.getJobName())
                .scheduledDate(job == null ? null : job.getScheduledDate())
                .requestedAt(request.getRequestedAt())
                .build();
    }

    /** Summary only (F1): counts and district / province names — never customer names, phones or address lines. */
    private DriverRequestResponse toSummary(JobDriverRequest request, DeliveryJob job, List<DeliveryRouting> stops,
                                            User owner, Map<String, String> areaNames) {
        List<DeliveryRouting> open = stops.stream()
                .filter(r -> !"CANCELLED".equalsIgnoreCase(r.getRoutingStatus()))
                .sorted(Comparator.comparing(DeliveryRouting::getSequenceNo, Comparator.nullsLast(Integer::compare)))
                .toList();
        LinkedHashSet<String> areas = new LinkedHashSet<>();
        for (DeliveryRouting stop : open) {
            OrderAddress address = stop.getOrder() == null ? null : stop.getOrder().getAddress();
            if (address != null) {
                String area = areaText(address.getProvinceCode(), address.getDistrictCode(), areaNames);
                if (area != null) {
                    areas.add(area);
                }
            }
        }
        return DriverRequestResponse.builder()
                .requestId(request.getRequestId())
                .requestStatus(request.getRequestStatus())
                .jobId(job.getJobId())
                .jobCode(job.getJobCode())
                .jobName(job.getJobName())
                .scheduledDate(job.getScheduledDate())
                .stopCount(open.size())
                .originText(areaText(job.getOriginProvinceCode(), job.getOriginDistrictCode(), areaNames))
                .stopAreas(List.copyOf(areas))
                .owner(TeamService.toSummary(owner))
                .requestedAt(request.getRequestedAt())
                .build();
    }

    /** "อ.เมืองสกลนคร จ.สกลนคร" — names looked up once per district within one call. */
    private String areaText(String provinceCode, String districtCode, Map<String, String> cache) {
        if (provinceCode == null) {
            return null;
        }
        return cache.computeIfAbsent(provinceCode + ":" + Objects.toString(districtCode, ""), key -> {
            AddressInfoDto info = masterDataService.getAddressInfoSafe(provinceCode, districtCode, null);
            String text = String.join(" ",
                    StringUtils.hasText(info.getDistrictName()) ? "อ." + info.getDistrictName() : "",
                    StringUtils.hasText(info.getProvinceName()) ? "จ." + info.getProvinceName() : "").trim();
            return text.isEmpty() ? null : text;
        });
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
