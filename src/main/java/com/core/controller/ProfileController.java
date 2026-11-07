package com.core.controller;

import com.common.dto.ApiResponse;
import com.core.dto.AvailabilityRequest;
import com.core.dto.ProfileResponse;
import com.core.dto.ProfileUpdateRequest;
import com.core.service.profile.ProfileService;
import com.core.service.security.SecurityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * The caller's own profile. The paths are part of the permission rows in db/permissions-profile-data.sql.
 */
@RestController
@RequestMapping("/endpoint-core-service/api/profile")
@RequiredArgsConstructor
@Tag(name = "Profile", description = "Own phone, home address and driver availability")
public class ProfileController {

    private final ProfileService profileService;
    private final SecurityService securityService;

    @Operation(summary = "Get my profile",
            description = "Name, phone, home address; availability + delivering for a DRIVER account")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<ProfileResponse>> getMyProfile(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader) {
        String username = securityService.extractUsernameFromToken(authHeader);
        return ResponseEntity.ok(ApiResponse.success(profileService.getMyProfile(username)));
    }

    @Operation(summary = "Update my contact details",
            description = "All fields optional, blank = cleared. Phone: 10 digits starting with 0. "
                    + "subdistrictCode = null clears the whole address.")
    @PutMapping("/me")
    public ResponseEntity<ApiResponse<ProfileResponse>> updateMyProfile(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody ProfileUpdateRequest request) {
        String username = securityService.extractUsernameFromToken(authHeader);
        return ResponseEntity.ok(ApiResponse.success(profileService.updateMyProfile(username, request)));
    }

    @Operation(summary = "Set my availability", description = "DRIVER only: AVAILABLE, BUSY or OFF")
    @PutMapping("/me/availability")
    public ResponseEntity<ApiResponse<ProfileResponse>> updateAvailability(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody AvailabilityRequest request) {
        String username = securityService.extractUsernameFromToken(authHeader);
        return ResponseEntity.ok(ApiResponse.success(profileService.updateAvailability(username, request)));
    }
}
