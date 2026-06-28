package com.search.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Entity(name = "SearchDeliveryRouting")
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "delivery_routing", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"job_id", "order_id"})
})
public class DeliveryRouting {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "delivery_routing_seq")
    @SequenceGenerator(name = "delivery_routing_seq", sequenceName = "delivery_routing_route_id_seq", allocationSize = 1)
    @Column(name = "route_id")
    private Long routeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id")
    private DeliveryJob deliveryJob;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    @Column(name = "sequence_no", nullable = false)
    private Integer sequenceNo;

    @Column(name = "routing_status")
    @Builder.Default
    private String routingStatus = "new";

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "updated_by")
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
