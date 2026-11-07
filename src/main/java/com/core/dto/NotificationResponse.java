package com.core.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "One in-app notification")
public class NotificationResponse {

    @Schema(description = "Notification ID", example = "41")
    private Long notificationId;

    @Schema(description = "Notification type", example = "JOB_DUE_TOMORROW")
    private String type;

    @Schema(description = "Short title", example = "พรุ่งนี้มีนัดส่งงาน")
    private String title;

    @Schema(description = "Full message, as written when the notification was created",
            example = "พรุ่งนี้ (พฤหัส 22 ต.ค.) มีนัดส่งงาน “ส่งสกลนคร รอบเช้า” · 5 จุด: สมชาย, สมหญิง และอีก 3 ราย")
    private String message;

    @Schema(description = "Related job, if any", example = "123")
    private Long jobId;

    @Schema(description = "Date the notification is about, if any", example = "2026-10-22")
    private LocalDate targetDate;

    @Schema(description = "Whether the recipient has read it", example = "false")
    private boolean read;

    @Schema(description = "When it was read; null while unread")
    private LocalDateTime readAt;

    @Schema(description = "When it was created", example = "2026-10-21T08:05:00")
    private LocalDateTime createdAt;
}
