package com.core.service.notification;

import com.authen.entity.User;
import com.core.constant.NotificationType;
import com.core.entity.DeliveryJob;
import com.core.entity.Notification;
import com.core.entity.TeamMember;
import com.core.service.notification.reminder.JobDueMessageBuilder;
import com.core.service.team.TeamService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Notifications about teams, job requests and assignment (US-TEAM-10, 12–14). Called inside the transaction of the change, so a
 * rolled-back action leaves no notification. Each team row changes status once, so its id is the dedupe key;
 * assignment can repeat, so its key carries the time.
 */
@Component
@RequiredArgsConstructor
public class TeamNotifier {

    private static final DateTimeFormatter THAI_SHORT_DATE =
            DateTimeFormatter.ofPattern("EEE d MMM", Locale.of("th", "TH"));

    private final NotificationPublisher notificationPublisher;
    private final Clock clock;

    public void invited(TeamMember row, User owner, User driver) {
        team(driver, NotificationType.TEAM_INVITED, row, "คำเชิญเข้าทีม", name(owner) + " เชิญคุณเข้าทีม");
    }

    public void answered(TeamMember row, User owner, User driver, boolean accepted) {
        if (accepted) {
            team(owner, NotificationType.TEAM_INVITE_ACCEPTED, row, "สมาชิกใหม่", name(driver) + " ตอบรับคำเชิญแล้ว");
        } else {
            team(owner, NotificationType.TEAM_INVITE_DECLINED, row, "คำเชิญถูกปฏิเสธ", name(driver) + " ปฏิเสธคำเชิญ");
        }
    }

    public void removed(TeamMember row, User owner, User driver) {
        team(driver, NotificationType.TEAM_REMOVED, row, "ออกจากทีมแล้ว", name(owner) + " นำคุณออกจากทีม");
    }

    public void left(TeamMember row, User owner, User driver) {
        team(owner, NotificationType.TEAM_MEMBER_LEFT, row, "สมาชิกออกจากทีม", name(driver) + " ออกจากทีมแล้ว");
    }

    /** {@code มีงานรอคุณตอบรับ · “ส่งสกลนคร” นัดส่ง พ. 22 ต.ค. · 5 จุด · จาก สมชาย} */
    public void jobRequested(DeliveryJob job, User owner, User driver, int stopCount) {
        StringBuilder message = new StringBuilder(quoted(job));
        if (job.getScheduledDate() != null) {
            message.append(" นัดส่ง ").append(THAI_SHORT_DATE.format(job.getScheduledDate()));
        }
        message.append(" · ").append(stopCount).append(" จุด · จาก ").append(name(owner));
        job(driver, NotificationType.JOB_REQUESTED, job, "มีงานรอคุณตอบรับ", message.toString());
    }

    /** To the owner: {@code ประยุทธ รับงาน “…” แล้ว} / {@code ประยุทธ ปฏิเสธงาน “…” · ไกลเกินไป}. */
    public void jobRequestAnswered(DeliveryJob job, User owner, User driver, boolean accepted, String reasonText) {
        if (accepted) {
            job(owner, NotificationType.JOB_REQUEST_ACCEPTED, job, "คนส่งรับงานแล้ว",
                    name(driver) + " รับงาน " + quoted(job) + " แล้ว");
            return;
        }
        String message = name(driver) + " ปฏิเสธงาน " + quoted(job)
                + (reasonText == null || reasonText.isBlank() ? "" : " · " + reasonText);
        job(owner, NotificationType.JOB_REQUEST_DECLINED, job, "คนส่งปฏิเสธงาน", message);
    }

    public void jobRequestCancelled(DeliveryJob job, User driver) {
        job(driver, NotificationType.JOB_REQUEST_CANCELLED, job, "คำขอรับงานถูกยกเลิก", quoted(job));
    }

    public void jobUnassigned(DeliveryJob job, User driver) {
        job(driver, NotificationType.JOB_UNASSIGNED, job, "งานถูกยกเลิกการมอบหมาย",
                "“" + JobDueMessageBuilder.jobLabel(job) + "”");
    }

    /** To the owner: the driver did not answer in time — one per request. */
    public void jobRequestExpired(DeliveryJob job, JobDriverRequestKey request, User owner, User driver) {
        job(owner, NotificationType.JOB_REQUEST_EXPIRED, job, "คนส่งไม่ตอบคำขอ",
                name(driver) + " ไม่ตอบคำขอ " + quoted(job) + " ภายในเวลา · เลือกคนส่งใหม่ได้",
                NotificationType.JOB_REQUEST_EXPIRED + ":" + request.requestId());
    }

    /** To the owner: {@code ประยุทธ คืนงาน “…” · รถไม่พร้อม · เลือกคนส่งใหม่ได้}. */
    public void jobReleased(DeliveryJob job, User owner, User driver, String reasonText) {
        String message = name(driver) + " คืนงาน " + quoted(job)
                + (reasonText == null || reasonText.isBlank() ? "" : " · " + reasonText) + " · เลือกคนส่งใหม่ได้";
        job(owner, NotificationType.JOB_RELEASED, job, "คนส่งคืนงาน", message);
    }

    /** Request id only — keeps this class free of the request entity. */
    public record JobDriverRequestKey(Long requestId) {
    }

    /** To the owner: {@code ประยุทธ แจ้งว่า คุณสมใจ ยกเลิก · ลูกค้าเปลี่ยนใจ · “…”}. */
    public void stopCancelRequested(DeliveryJob job, User owner, User driver, String customerName, String reasonText) {
        String message = name(driver) + " แจ้งว่า " + customer(customerName) + " ยกเลิก"
                + (reasonText == null || reasonText.isBlank() ? "" : " · " + reasonText) + " · " + quoted(job);
        job(owner, NotificationType.STOP_CANCEL_REQUESTED, job, "แจ้งยกเลิกจุดส่ง รออนุมัติ", message);
    }

    /** To the driver: the owner approved / rejected their cancel report. */
    public void stopCancelAnswered(DeliveryJob job, User driver, String customerName, boolean approved) {
        if (approved) {
            job(driver, NotificationType.STOP_CANCEL_APPROVED, job, "อนุมัติยกเลิกแล้ว",
                    customer(customerName) + " ยกเลิกแล้ว · " + quoted(job));
        } else {
            job(driver, NotificationType.STOP_CANCEL_REJECTED, job, "ไม่อนุมัติการยกเลิก",
                    customer(customerName) + " ยังต้องส่ง · " + quoted(job));
        }
    }

    private static String customer(String customerName) {
        return customerName == null || customerName.isBlank() ? "ลูกค้า" : customerName;
    }

    private void team(User recipient, String type, TeamMember row, String title, String message) {
        if (recipient == null) {
            return;
        }
        notificationPublisher.publish(Notification.builder()
                .recipientUsername(recipient.getUsername())
                .notificationType(type)
                .title(title)
                .message(message)
                .dedupeKey(type + ":" + row.getTeamMemberId())
                .build());
    }

    private void job(User recipient, String type, DeliveryJob job, String title, String message) {
        job(recipient, type, job, title, message, type + ":" + job.getJobId() + ":" + LocalDateTime.now(clock));
    }

    private void job(User recipient, String type, DeliveryJob job, String title, String message, String dedupeKey) {
        if (recipient == null) {
            return;
        }
        notificationPublisher.publish(Notification.builder()
                .recipientUsername(recipient.getUsername())
                .notificationType(type)
                .title(title)
                .message(message)
                .jobId(job.getJobId())
                .dedupeKey(dedupeKey)
                .build());
    }

    private static String quoted(DeliveryJob job) {
        return "“" + JobDueMessageBuilder.jobLabel(job) + "”";
    }

    private static String name(User user) {
        return user == null ? "" : TeamService.toSummary(user).getDisplayName();
    }
}
