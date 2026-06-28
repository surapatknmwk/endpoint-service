package com.authen.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.common.dto.ApiResponse;
import com.authen.dto.CheckPermissionRequest;
import com.authen.dto.LoginRequest;
import com.authen.dto.LoginResponse;
import com.authen.service.AuthenticationService;

@Slf4j
@RestController
@RequestMapping("/endpoint-authen-service/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Authentication management APIs")
public class AuthenticationController {

    private final AuthenticationService authenticationService;

    @Operation(
            summary = "User login",
            description = "Authenticate user with username and password, returns JWT token"
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Login successful",
                    content = @Content(schema = @Schema(implementation = LoginResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid credentials or user account disabled"
            )
    })
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        try {
            LoginResponse response = authenticationService.login(request, httpRequest);
            return ResponseEntity.ok(ApiResponse.success(response, "Login successful"));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    @Operation(
            summary = "User logout",
            description = "Invalidate the current JWT token and end user session",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Logout successful"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid token or logout failed"
            )
    })
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @Parameter(description = "JWT token in format: Bearer {token}", required = true)
            @RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");
            authenticationService.logout(token);
            return ResponseEntity.ok(ApiResponse.success(null, "Logout successful"));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    @Operation(
            summary = "Validate JWT token",
            description = "Check if the provided JWT token is valid and not expired",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Token validation completed"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid token format or validation failed"
            )
    })
    @GetMapping("/validate")
    public ResponseEntity<ApiResponse<Boolean>> validateToken(
            @Parameter(description = "JWT token in format: Bearer {token}", required = true)
            @RequestHeader("Authorization") String authHeader) {
        try {
            log.info("[/validate] Authorization : {}",authHeader);
            String token = authHeader.replace("Bearer ", "");
            boolean isValid = authenticationService.validateToken(token);
            if (isValid) {
                return ResponseEntity.ok(ApiResponse.success(isValid, "Token validation completed"));
            } else {
                return ResponseEntity.badRequest().body(ApiResponse.error("Token Validation Failed"));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    @Operation(
            summary = "Check API permission",
            description = "Verify if the role from the token has permission to access the specified API endpoint",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Permission check completed"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid token or request"
            )
    })
    @PostMapping("/check-permission")
    public ResponseEntity<ApiResponse<Boolean>> checkPermission(
            @Parameter(description = "JWT token in format: Bearer {token}", required = true)
            @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody CheckPermissionRequest request) {
        try {
            log.info("[/check-permission] Authorization : {} request : {}",authHeader,request);
            String token = authHeader.replace("Bearer ", "");
            boolean hasPermission = authenticationService.checkPermission(
                    token,
                    request.getMethod(),
                    request.getApi()
            );
            String message = hasPermission ? "Permission granted" : "Permission denied";
            return ResponseEntity.ok(ApiResponse.success(hasPermission, message));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    @Operation(
            summary = "Refresh JWT token",
            description = "Generate a new JWT token using the current valid token",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Token refreshed successfully",
                    content = @Content(schema = @Schema(implementation = LoginResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid or expired token"
            )
    })
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<LoginResponse>> refreshToken(
            @Parameter(description = "JWT token in format: Bearer {token}", required = true)
            @RequestHeader("Authorization") String authHeader,
            HttpServletRequest httpRequest) {
        try {
            String token = authHeader.replace("Bearer ", "");
            LoginResponse response = authenticationService.refreshToken(token, httpRequest);
            return ResponseEntity.ok(ApiResponse.success(response, "Token refreshed successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

}
