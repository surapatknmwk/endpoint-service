package com.core.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * A job an owner asked me to take — driver view (team.md US-TEAM-12). Only a summary: no customer names, phones
 * or addresses until the driver accepts (design assignConfirm F1).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "A request to take a job — summary for the driver, without customer data")
public class DriverRequestResponse {

    @Schema(description = "Request ID", example = "5")
    private Long requestId;

    @Schema(description = "PENDING, ACCEPTED, DECLINED or CANCELLED", example = "PENDING")
    private String requestStatus;

    @Schema(description = "Job ID", example = "12")
    private Long jobId;

    @Schema(description = "Job code", example = "JOB-20261103-1A2B3C4D")
    private String jobCode;

    @Schema(description = "Job name; null when not named", example = "รอบเช้า สกลนคร")
    private String jobName;

    @Schema(description = "Scheduled delivery date; null when not set")
    private LocalDateTime scheduledDate;

    @Schema(description = "Stops that are not cancelled", example = "5")
    private int stopCount;

    @Schema(description = "Origin as district + province", example = "อ.เมืองสกลนคร จ.สกลนคร")
    private String originText;

    @Schema(description = "District + province of the stops, distinct, in route order",
            example = "[\"อ.เมืองสกลนคร จ.สกลนคร\", \"อ.พรรณานิคม จ.สกลนคร\"]")
    private List<String> stopAreas;

    @Schema(description = "Jobs I already took (any owner, not completed) scheduled on the same day; 0 when the "
            + "request has no date", example = "2")
    private int sameDayJobCount;

    @Schema(description = "Stops (not cancelled) of those same-day jobs", example = "14")
    private int sameDayStopCount;

    @Schema(description = "The owner who asked")
    private TeamUserSummary owner;

    @Schema(description = "When the owner asked")
    private LocalDateTime requestedAt;

    @Schema(description = "Answer before this time, or the request expires (pending only)")
    private LocalDateTime expiresAt;
}
