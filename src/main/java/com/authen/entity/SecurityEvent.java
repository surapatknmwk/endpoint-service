package com.authen.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * One detected risky activity, aggregated: identical events inside a flush interval are a
 * single row with {@code occurrenceCount} and first/last seen times.
 */
@Entity
@Table(name = "security_events")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SecurityEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "security_events_event_id_seq")
    @SequenceGenerator(name = "security_events_event_id_seq", sequenceName = "security_events_event_id_seq", allocationSize = 1)
    @Column(name = "event_id")
    private Long eventId;

    @Column(name = "event_type", length = 50, nullable = false)
    private String eventType;

    @Column(name = "severity", length = 10, nullable = false)
    private String severity;

    @Column(name = "username", length = 50)
    private String username;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "http_method", length = 10)
    private String httpMethod;

    @Column(name = "request_path", length = 500)
    private String requestPath;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "detail", columnDefinition = "TEXT")
    private String detail;

    @Column(name = "occurrence_count", nullable = false)
    private Integer occurrenceCount;

    @Column(name = "first_seen_at", nullable = false)
    private LocalDateTime firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private LocalDateTime lastSeenAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
