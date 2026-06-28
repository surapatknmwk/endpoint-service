package com.core.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "delivery_routing", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"job_id", "order_id"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryRouting {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "delivery_routing_seq")
    @SequenceGenerator(name = "delivery_routing_seq", sequenceName = "delivery_routing_route_id_seq", allocationSize = 1)
    @Column(name = "route_id")
    private Long routeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", nullable = false)
    private DeliveryJob deliveryJob;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "sequence_no", nullable = false)
    private Integer sequenceNo;

    @Column(name = "routing_status", length = 20)
    @Builder.Default
    private String routingStatus = "new";

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by", length = 20)
    private String createdBy;

    @Column(name = "updated_by", length = 20)
    private String updatedBy;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
