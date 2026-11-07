package com.core.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** One not-completed job of the owner assigned to a driver — no customer data, no price (team.md US-TEAM-03). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "A not-completed job assigned to the driver, in short — owner view of a team member")
public class OpenJobSummary {

    @Schema(description = "Job ID", example = "41")
    private Long jobId;

    @Schema(description = "Unique job code", example = "JOB-20261103-A1B2C3D4")
    private String jobCode;

    @Schema(description = "Job name; null when not set", example = "รอบเช้า สกลนคร")
    private String jobName;

    @Schema(description = "NEW (รอออกรถ) or IN_PROGRESS (กำลังส่ง)", example = "IN_PROGRESS")
    private String deliveryStatus;

    @Schema(description = "Scheduled delivery date; null when not set")
    private LocalDateTime scheduledDate;

    @Schema(description = "All stops of the job (same total as the job card)", example = "5")
    private long stopCount;

    @Schema(description = "Stops delivered (COMPLETED)", example = "2")
    private long completedStopCount;
}
