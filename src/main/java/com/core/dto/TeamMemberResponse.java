package com.core.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "A member (or pending invitation) of the caller's team — owner view")
public class TeamMemberResponse {

    @Schema(description = "Team member row ID", example = "12")
    private Long teamMemberId;

    @Schema(description = "The driver")
    private TeamUserSummary member;

    @Schema(description = "INVITED, ACTIVE, DECLINED, CANCELLED, REMOVED or LEFT", example = "ACTIVE")
    private String memberStatus;

    @Schema(description = "When the invitation was sent")
    private LocalDateTime invitedAt;

    @Schema(description = "When the driver answered; null while INVITED")
    private LocalDateTime respondedAt;

    @Schema(description = "Assigned jobs of this owner that are not completed", example = "2")
    private long openJobCount;

    @Schema(description = "Those open jobs in short: started first, then by scheduled date (no date last); "
            + "empty for INVITED rows")
    private List<OpenJobSummary> openJobs;

    @Schema(description = "The driver's phone and home area; only while INVITED / ACTIVE")
    private ContactInfo contact;

    @Schema(description = "AVAILABLE, BUSY or OFF (set by the driver); null when the row is not open", example = "AVAILABLE")
    private String availability;

    @Schema(description = "The driver has a started job in some team they are ACTIVE in (กำลังส่งงาน) — not said whose")
    private boolean delivering;

    @Schema(description = "Last use of the app; null = never or not shown")
    private LocalDateTime lastSeenAt;

    @Schema(description = "Jobs of this owner waiting for this driver's answer (not counted in openJobCount)",
            example = "1")
    private long pendingRequestCount;
}
