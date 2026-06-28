package com.core.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "delivery_jobs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryJob {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "delivery_jobs_seq")
    @SequenceGenerator(name = "delivery_jobs_seq", sequenceName = "delivery_jobs_job_id_seq", allocationSize = 1)
    @Column(name = "job_id")
    private Long jobId;

    @Column(name = "job_code", nullable = false, unique = true, length = 50)
    private String jobCode;

    @Column(name = "job_name", length = 255)
    private String jobName;

    @Column(name = "total_orders")
    @Builder.Default
    private Integer totalOrders = 0;

    @Column(name = "delivery_status", length = 20)
    @Builder.Default
    private String deliveryStatus = "new";

    @Column(name = "status", length = 1)
    @Builder.Default
    private String status = "A";

    @Column(name = "scheduled_date")
    private LocalDateTime scheduledDate;

    @Column(name = "completed_date")
    private LocalDateTime completedDate;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by", length = 20)
    private String createdBy;

    @Column(name = "updated_by", length = 20)
    private String updatedBy;

    @OneToMany(mappedBy = "deliveryJob", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<DeliveryRouting> routings;

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
