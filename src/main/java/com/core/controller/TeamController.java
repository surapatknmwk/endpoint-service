package com.core.controller;

import com.common.dto.ApiResponse;
import com.core.dto.TeamInviteRequest;
import com.core.dto.TeamMemberResponse;
import com.core.dto.TeamMembershipResponse;
import com.core.dto.TeamSettingsDto;
import com.core.service.security.SecurityService;
import com.core.service.team.TeamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Team of an owner and the drivers in it. The path variable names are part of the permission rows in
 * db/permissions-team-data.sql (the filter checks the matched pattern), so rename them only together.
 */
@RestController
@RequestMapping("/endpoint-core-service/api/team")
@RequiredArgsConstructor
@Tag(name = "Team", description = "Owner invites drivers; drivers answer invitations and leave teams")
public class TeamController {

    private final TeamService teamService;
    private final SecurityService securityService;

    // ---- owner ----

    @Operation(summary = "List my team", description = "Owner: INVITED and ACTIVE rows, with open assigned jobs per driver")
    @GetMapping("/members")
    public ResponseEntity<ApiResponse<List<TeamMemberResponse>>> listMembers(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader) {
        String username = securityService.extractUsernameFromToken(authHeader);
        return ResponseEntity.ok(ApiResponse.success(teamService.listMembers(username)));
    }

    @Operation(summary = "Invite a driver",
            description = "Owner: invites an active DRIVER account by username (trimmed, case-insensitive). "
                    + "Unknown / not a driver / inactive / self all answer \"ไม่สามารถเชิญผู้ใช้นี้ได้\".")
    @PostMapping("/members")
    public ResponseEntity<ApiResponse<TeamMemberResponse>> invite(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody TeamInviteRequest request) {
        String username = securityService.extractUsernameFromToken(authHeader);
        TeamMemberResponse data = teamService.invite(username, request.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(data, "Invitation sent"));
    }

    @Operation(summary = "Withdraw an invitation or remove a member",
            description = "Owner: INVITED → CANCELLED, ACTIVE → REMOVED. A member with open assigned jobs cannot be removed.")
    @DeleteMapping("/members/{teamMemberId}")
    public ResponseEntity<ApiResponse<TeamMemberResponse>> removeMember(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader,
            @PathVariable Long teamMemberId) {
        String username = securityService.extractUsernameFromToken(authHeader);
        return ResponseEntity.ok(ApiResponse.success(teamService.removeMember(username, teamMemberId)));
    }

    @Operation(summary = "Get team settings", description = "Owner: whether drivers see prices (default false)")
    @GetMapping("/settings")
    public ResponseEntity<ApiResponse<TeamSettingsDto>> getSettings(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader) {
        String username = securityService.extractUsernameFromToken(authHeader);
        return ResponseEntity.ok(ApiResponse.success(teamService.getSettings(username)));
    }

    @Operation(summary = "Update team settings", description = "Owner: takes effect on the drivers' next load")
    @PutMapping("/settings")
    public ResponseEntity<ApiResponse<TeamSettingsDto>> updateSettings(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody TeamSettingsDto request) {
        String username = securityService.extractUsernameFromToken(authHeader);
        return ResponseEntity.ok(ApiResponse.success(teamService.updateSettings(username, request)));
    }

    // ---- driver ----

    @Operation(summary = "List my invitations", description = "Driver: invitations not answered yet, newest first")
    @GetMapping("/invitations")
    public ResponseEntity<ApiResponse<List<TeamMembershipResponse>>> listInvitations(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader) {
        String username = securityService.extractUsernameFromToken(authHeader);
        return ResponseEntity.ok(ApiResponse.success(teamService.listInvitations(username)));
    }

    @Operation(summary = "Accept an invitation", description = "Driver: INVITED → ACTIVE")
    @PatchMapping("/invitations/{teamMemberId}/accept")
    public ResponseEntity<ApiResponse<TeamMembershipResponse>> acceptInvitation(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader,
            @PathVariable Long teamMemberId) {
        String username = securityService.extractUsernameFromToken(authHeader);
        return ResponseEntity.ok(ApiResponse.success(teamService.acceptInvitation(username, teamMemberId)));
    }

    @Operation(summary = "Decline an invitation", description = "Driver: INVITED → DECLINED")
    @PatchMapping("/invitations/{teamMemberId}/decline")
    public ResponseEntity<ApiResponse<TeamMembershipResponse>> declineInvitation(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader,
            @PathVariable Long teamMemberId) {
        String username = securityService.extractUsernameFromToken(authHeader);
        return ResponseEntity.ok(ApiResponse.success(teamService.declineInvitation(username, teamMemberId)));
    }

    @Operation(summary = "List my teams", description = "Driver: teams I am an ACTIVE member of, with my open jobs in each")
    @GetMapping("/memberships")
    public ResponseEntity<ApiResponse<List<TeamMembershipResponse>>> listMemberships(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader) {
        String username = securityService.extractUsernameFromToken(authHeader);
        return ResponseEntity.ok(ApiResponse.success(teamService.listMemberships(username)));
    }

    @Operation(summary = "Leave a team", description = "Driver: ACTIVE → LEFT. Not while I have open jobs of that team.")
    @PatchMapping("/memberships/{teamMemberId}/leave")
    public ResponseEntity<ApiResponse<TeamMembershipResponse>> leaveTeam(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader,
            @PathVariable Long teamMemberId) {
        String username = securityService.extractUsernameFromToken(authHeader);
        return ResponseEntity.ok(ApiResponse.success(teamService.leaveTeam(username, teamMemberId)));
    }
}
