package com.core.dto;

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
@Schema(description = "An invitation to, or membership of, an owner's team — driver view")
public class TeamMembershipResponse {

    @Schema(description = "Team member row ID", example = "12")
    private Long teamMemberId;

    @Schema(description = "The owner of the team")
    private TeamUserSummary owner;

    @Schema(description = "INVITED, ACTIVE, DECLINED, CANCELLED, REMOVED or LEFT", example = "INVITED")
    private String memberStatus;

    @Schema(description = "When the invitation was sent")
    private LocalDateTime invitedAt;

    @Schema(description = "When the driver answered; null while INVITED")
    private LocalDateTime respondedAt;

    @Schema(description = "My assigned jobs of this owner that are not completed", example = "1")
    private long openJobCount;

    @Schema(description = "The owner's phone and address; only while INVITED / ACTIVE")
    private ContactInfo contact;
}
