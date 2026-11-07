package com.core.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * A system announcement (ปิดปรับปรุง / กรณีพิเศษ) typed into the database by an admin. The announcement job
 * turns each due row into one notification per recipient — see
 * {@link com.core.service.notification.announcement.AnnouncementService}.
 */
@Entity
@Table(name = "system_announcements")
@Data
@NoArgsConstructor
public class SystemAnnouncement {

    public static final String AUDIENCE_ALL = "ALL";
    public static final String AUDIENCE_OWNER = "OWNER";
    public static final String AUDIENCE_DRIVER = "DRIVER";

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "system_announcements_seq")
    @SequenceGenerator(name = "system_announcements_seq", sequenceName = "system_announcements_announcement_id_seq", allocationSize = 1)
    @Column(name = "announcement_id")
    private Long announcementId;

    @Column(name = "title", length = 200, nullable = false)
    private String title;

    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "audience", length = 10, nullable = false)
    private String audience;

    @Column(name = "send_line", nullable = false)
    private boolean sendLine;

    @Column(name = "publish_at", nullable = false)
    private LocalDateTime publishAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "status", length = 1, nullable = false)
    private String status;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "recipient_count")
    private Integer recipientCount;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by", length = 50, nullable = false)
    private String createdBy;

    public String dedupeKey() {
        return dedupeKey(announcementId);
    }

    public static String dedupeKey(Long announcementId) {
        return "SYSTEM_ANNOUNCEMENT:" + announcementId;
    }
}
