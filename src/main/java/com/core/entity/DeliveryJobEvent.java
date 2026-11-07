package com.core.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** One action on a job — who, what, when (audit log). Written once, never updated. */
@Entity
@Table(name = "delivery_job_events")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryJobEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "event_id")
    private Long eventId;

    @Column(name = "job_id", nullable = false)
    private Long jobId;

    // null = the whole job (start / edit / close)
    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "event_type", nullable = false, length = 30)
    private String eventType;

    @Column(name = "reason", length = 30)
    private String reason;

    @Column(name = "note", length = 200)
    private String note;

    @Column(name = "actor_username", nullable = false, length = 50)
    private String actorUsername;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // ตำแหน่งตอนกด (ไม่บังคับ) + ระยะเส้นตรงถึงจุดกลางตำบลของลูกค้า
    @Column(name = "lat", precision = 9, scale = 6)
    private BigDecimal lat;

    @Column(name = "lng", precision = 9, scale = 6)
    private BigDecimal lng;

    @Column(name = "accuracy_m")
    private Integer accuracyM;

    @Column(name = "distance_km", precision = 8, scale = 2)
    private BigDecimal distanceKm;
}
