package com.core.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * In-app notification shown under the bell icon. Rows are created only through
 * {@link com.core.service.notification.NotificationPublisher} so the dedupe key is always respected.
 */
@Entity
@Table(name = "notifications", uniqueConstraints = {
        @UniqueConstraint(name = "uq_notifications_recipient_dedupe", columnNames = {"recipient_username", "dedupe_key"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamicUpdate // markRead writes only read_at, so it never overwrites line_* set by LineDeliveryService meanwhile
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "notifications_seq")
    @SequenceGenerator(name = "notifications_seq", sequenceName = "notifications_notification_id_seq", allocationSize = 1)
    @Column(name = "notification_id")
    private Long notificationId;

    @Column(name = "recipient_username", nullable = false, length = 50)
    private String recipientUsername;

    @Column(name = "notification_type", nullable = false, length = 50)
    private String notificationType;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "job_id")
    private Long jobId;

    @Column(name = "target_date")
    private LocalDate targetDate;

    @Column(name = "dedupe_key", nullable = false, length = 200)
    private String dedupeKey;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // ส่งสำเนาเข้า LINE (design lineNotify): null = ไม่ส่ง · PENDING = รอ job ส่ง · SENT / SKIPPED / FAILED = จบแล้ว
    @Column(name = "line_status", length = 10)
    private String lineStatus;

    @Builder.Default
    @Column(name = "line_attempts", nullable = false)
    private Integer lineAttempts = 0;

    @Column(name = "line_sent_at")
    private LocalDateTime lineSentAt;

    @Column(name = "line_error", length = 200)
    private String lineError;
}
