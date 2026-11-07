package com.core.service.notification.reminder;

import com.authen.entity.User;
import com.authen.repository.UserRepository;
import com.core.constant.JobStatus;
import com.core.constant.NotificationType;
import com.core.entity.DeliveryJob;
import com.core.entity.Notification;
import com.core.repository.DeliveryJobRepository;
import com.core.repository.TeamMemberRepository;
import com.core.service.notification.NotificationPublisher;
import com.core.service.team.JobRequestPolicy;
import com.core.service.team.TeamService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * "ถึงวันนัดแล้ว งานยังไม่เริ่ม" (driver-risk ข้อ 16): a job scheduled today that a driver accepted but has not
 * started by {@link JobRequestPolicy#lateFrom} — tell the owner (to call or change the driver) and the driver
 * (if still ACTIVE in the owner's team). Once per job, recipient and day (dedupe key), so it is safe to run hourly.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobLateStartService {

    static final String TITLE = "งานวันนี้ยังไม่เริ่ม";

    private final DeliveryJobRepository deliveryJobRepository;
    private final NotificationPublisher notificationPublisher;
    private final UserRepository userRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final JobRequestPolicy jobRequestPolicy;
    private final Clock clock;

    @Transactional
    public int run() {
        LocalDate today = LocalDate.now(clock);
        if (LocalDateTime.now(clock).isBefore(jobRequestPolicy.lateFrom(today))) {
            return 0;
        }
        List<DeliveryJob> jobs = deliveryJobRepository.findScheduledBetween(
                JobStatus.NEW, today.atStartOfDay(), today.plusDays(1).atStartOfDay());
        int created = 0;
        for (DeliveryJob job : jobs) {
            if (job.getAssignedDriverId() == null || job.getCreatedBy() == null) {
                continue;
            }
            User driver = userRepository.findById(job.getAssignedDriverId()).orElse(null);
            if (driver == null) {
                continue;
            }
            String label = "“" + JobDueMessageBuilder.jobLabel(job) + "”";
            String driverName = TeamService.toSummary(driver).getDisplayName();
            if (publish(job.getCreatedBy(), job, today,
                    driverName + " ยังไม่เริ่มงาน " + label + " ที่นัดวันนี้ · โทรถาม หรือเปลี่ยนคนส่งได้")) {
                created++;
            }
            if (teamMemberRepository.existsActiveMembership(job.getCreatedBy(), driver.getUserId())
                    && publish(driver.getUsername(), job, today,
                    "งาน " + label + " นัดวันนี้ ยังไม่ได้กดเริ่มงาน · ถ้าไปไม่ได้ ให้คืนงานหรือโทรบอกเจ้าของ")) {
                created++;
            }
        }
        log.info("Late-start reminders for {}: {} job(s) not started, {} created", today, jobs.size(), created);
        return created;
    }

    private boolean publish(String recipient, DeliveryJob job, LocalDate today, String message) {
        return notificationPublisher.publish(Notification.builder()
                .recipientUsername(recipient)
                .notificationType(NotificationType.JOB_NOT_STARTED)
                .title(TITLE)
                .message(message)
                .jobId(job.getJobId())
                .targetDate(today)
                .dedupeKey(NotificationType.JOB_NOT_STARTED + ":" + job.getJobId() + ":" + today)
                .build());
    }
}
