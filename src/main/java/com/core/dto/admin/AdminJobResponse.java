package com.core.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "A job in the admin list (admin.md US-ADM-04)")
public class AdminJobResponse {

    private Long jobId;

    @Schema(example = "JOB-20261107-0001")
    private String jobCode;

    private String jobName;

    @Schema(description = "Owner username (delivery_jobs.created_by)", example = "owner1")
    private String owner;

    @Schema(example = "IN_PROGRESS")
    private String deliveryStatus;

    private LocalDateTime scheduledDate;

    private Integer stopCount;

    private Integer completedStopCount;

    @Schema(description = "Assigned driver username; null = none", example = "driver1")
    private String driverUsername;

    private LocalDateTime createdAt;
}
