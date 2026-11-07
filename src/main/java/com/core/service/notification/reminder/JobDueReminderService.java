package com.core.service.notification.reminder;

import com.core.constant.JobDriverRequestStatus;
import com.core.constant.JobStatus;
import com.core.constant.NotificationType;
import com.core.entity.DeliveryJob;
import com.core.entity.Notification;
import com.core.repository.DeliveryJobRepository;
import com.core.repository.DeliveryRoutingRepository;
import com.core.repository.JobDriverRequestRepository;
import com.authen.repository.UserRepository;
import com.core.repository.NotificationRepository;
import com.core.repository.TeamMemberRepository;
import com.core.service.notification.NotificationPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * แจ้งเตือนเจ้าของงาน และคนส่งที่ถูก assign (ถ้ายังเป็นสมาชิก ACTIVE ของทีม) ว่า "พรุ่งนี้มีนัดส่งงาน"
 * สำหรับงานที่นัดส่งพรุ่งนี้ (เวลาไทย) และยังไม่ออกรถ (NEW)
 * ปลอดภัยถ้ารันซ้ำ: แต่ละงาน + วันนัด แจ้งได้ครั้งเดียวต่อผู้รับ (dedupe key)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobDueReminderService {

    static final String PENDING_SUFFIX = " · ยังรอคนส่งตอบรับ";

    private final DeliveryJobRepository deliveryJobRepository;
    private final DeliveryRoutingRepository deliveryRoutingRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationPublisher notificationPublisher;
    private final UserRepository userRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final JobDriverRequestRepository jobDriverRequestRepository;
    private final Clock clock;

    @Transactional
    public Result run() {
        LocalDate tomorrow = LocalDate.now(clock).plusDays(1);

        // ถอนก่อนสร้าง: งานที่เลื่อนนัดออกไปแล้วเลื่อนกลับมา จะได้แจ้งเตือนใหม่ได้โดยไม่ชน dedupe key
        int retracted = notificationRepository.deleteStaleJobReminders(NotificationType.JOB_DUE_TOMORROW)
                + notificationRepository.deleteRemindersOfFormerDrivers(NotificationType.JOB_DUE_TOMORROW);

        List<DeliveryJob> jobs = deliveryJobRepository.findScheduledBetween(
                JobStatus.NEW, tomorrow.atStartOfDay(), tomorrow.plusDays(1).atStartOfDay());

        int created = 0;
        for (DeliveryJob job : jobs) {
            if (job.getCreatedBy() == null || job.getCreatedBy().isBlank()) {
                continue;
            }
            String message = JobDueMessageBuilder.message(job, activeCustomerNames(job), tomorrow);
            // เจ้าของ: งานที่คนส่งยังไม่ตอบคำขอ ให้รู้ว่าต้องตาม (design assignConfirm G8)
            String ownerMessage = jobDriverRequestRepository.existsByJobIdAndRequestStatus(job.getJobId(),
                    JobDriverRequestStatus.PENDING) ? message + PENDING_SUFFIX : message;
            if (publish(job.getCreatedBy(), job, ownerMessage, tomorrow)) {
                created++;
            }
            String driver = activeDriverUsername(job);
            if (driver != null && publish(driver, job, message, tomorrow)) {
                created++;
            }
        }

        log.info("Job due reminders for {}: {} job(s) due, {} created, {} retracted",
                tomorrow, jobs.size(), created, retracted);
        return new Result(created, retracted);
    }

    private boolean publish(String recipient, DeliveryJob job, String message, LocalDate tomorrow) {
        return notificationPublisher.publish(Notification.builder()
                .recipientUsername(recipient)
                .notificationType(NotificationType.JOB_DUE_TOMORROW)
                .title(JobDueMessageBuilder.TITLE)
                .message(message)
                .jobId(job.getJobId())
                .targetDate(tomorrow)
                .dedupeKey(NotificationType.JOB_DUE_TOMORROW + ":" + job.getJobId() + ":" + tomorrow)
                .build());
    }

    /** The assigned driver while they can still open the job (ACTIVE in the owner's team); otherwise null. */
    private String activeDriverUsername(DeliveryJob job) {
        if (job.getAssignedDriverId() == null
                || !teamMemberRepository.existsActiveMembership(job.getCreatedBy(), job.getAssignedDriverId())) {
            return null;
        }
        return userRepository.findById(job.getAssignedDriverId()).map(u -> u.getUsername()).orElse(null);
    }

    private List<String> activeCustomerNames(DeliveryJob job) {
        return deliveryRoutingRepository.findByDeliveryJobJobIdOrderBySequenceNoAsc(job.getJobId()).stream()
                .filter(stop -> !"CANCELLED".equalsIgnoreCase(stop.getRoutingStatus()))
                .map(stop -> stop.getOrder().getCustomer().getName())
                .toList();
    }

    public record Result(int created, int retracted) {
    }
}
