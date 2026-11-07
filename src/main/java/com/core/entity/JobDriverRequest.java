package com.core.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * An owner's request to a driver to take a job, and the driver's answer
 * (see {@link com.core.constant.JobDriverRequestStatus}). Pending requests are created only through
 * {@link com.core.repository.JobDriverRequestRepository#insertPending} so the one-pending-per-job index is respected.
 */
@Entity
@Table(name = "job_driver_requests")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobDriverRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "job_driver_requests_seq")
    @SequenceGenerator(name = "job_driver_requests_seq", sequenceName = "job_driver_requests_request_id_seq",
            allocationSize = 1)
    @Column(name = "request_id")
    private Long requestId;

    @Column(name = "job_id", nullable = false)
    private Long jobId;

    @Column(name = "owner_user_id", nullable = false)
    private Integer ownerUserId;

    @Column(name = "driver_user_id", nullable = false)
    private Integer driverUserId;

    @Column(name = "request_status", nullable = false, length = 20)
    private String requestStatus;

    @Column(name = "decline_reason", length = 20)
    private String declineReason;

    @Column(name = "decline_note", length = 200)
    private String declineNote;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    @Column(name = "created_by", length = 50)
    private String createdBy;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by", length = 50)
    private String updatedBy;
}
