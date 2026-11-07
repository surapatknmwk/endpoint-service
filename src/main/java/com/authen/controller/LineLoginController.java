package com.authen.controller;

import com.authen.config.RegistrationProperties;
import com.authen.dto.LineAuthorizeResponse;
import com.authen.dto.LineCallbackRequest;
import com.authen.dto.LineCallbackResponse;
import com.authen.dto.LineLinkResponse;
import com.authen.dto.LineNotifyRequest;
import com.authen.dto.LineRegisterRequest;
import com.authen.dto.LoginResponse;
import com.authen.dto.RegisterConfigResponse;
import com.authen.service.LineLoginService;
import com.common.dto.ApiResponse;
import com.master.service.FeatureFlagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * LINE Login + sign-up (register.md US-REG-01 config, US-REG-07 … 10). Under /auth/** = public in
 * SecurityConfig; the link endpoints check the Bearer token in {@link LineLoginService}.
 * Errors are AuthApiException → GlobalExceptionHandler (status + user-safe Thai message).
 */
@RestController
@RequestMapping("/endpoint-authen-service/auth")
@RequiredArgsConstructor
@Tag(name = "LINE Login", description = "Log in, sign up and link with LINE")
public class LineLoginController {

    private final LineLoginService lineLoginService;
    private final RegistrationProperties properties;
    private final FeatureFlagService featureFlags;

    @Operation(summary = "Log-in / registration / LINE switches for the login page (feature flags, read live)")
    @GetMapping("/register/config")
    public ResponseEntity<ApiResponse<RegisterConfigResponse>> config() {
        return ResponseEntity.ok(ApiResponse.success(new RegisterConfigResponse(
                lineLoginService.isRegistrationEnabled(), false, properties.getTermsVersion(),
                lineLoginService.isLineEnabled(), featureFlags.isEnabled(FeatureFlagService.LOGIN))));
    }

    @Operation(summary = "Start a LINE log-in (purpose LOGIN) or link (purpose LINK, Bearer required)")
    @GetMapping("/line/authorize")
    public ResponseEntity<ApiResponse<LineAuthorizeResponse>> authorize(
            @RequestParam(defaultValue = "LOGIN") String purpose,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            HttpServletRequest request) {
        String url = lineLoginService.authorize(purpose, authHeader, request);
        return ResponseEntity.ok(ApiResponse.success(new LineAuthorizeResponse(url)));
    }

    @Operation(summary = "Finish a LINE log-in: LOGGED_IN (token) / REGISTER (ticket) / LINKED")
    @PostMapping("/line/callback")
    public ResponseEntity<ApiResponse<LineCallbackResponse>> callback(
            @Valid @RequestBody LineCallbackRequest body, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                lineLoginService.callback(body.getCode(), body.getState(), request)));
    }

    @Operation(summary = "Sign up with a LINE registration ticket; returns a token")
    @PostMapping("/register/line")
    public ResponseEntity<ApiResponse<LoginResponse>> register(
            @Valid @RequestBody LineRegisterRequest body, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(lineLoginService.register(body, request), "Registered"));
    }

    @Operation(summary = "Is my account linked to LINE")
    @GetMapping("/line/link")
    public ResponseEntity<ApiResponse<LineLinkResponse>> linkStatus(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        return ResponseEntity.ok(ApiResponse.success(lineLoginService.linkStatus(authHeader)));
    }

    @Operation(summary = "Turn LINE notifications on or off for my linked LINE account")
    @PatchMapping("/line/notify")
    public ResponseEntity<ApiResponse<LineLinkResponse>> setNotify(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @Valid @RequestBody LineNotifyRequest body) {
        return ResponseEntity.ok(ApiResponse.success(lineLoginService.setNotify(authHeader, body.getEnabled())));
    }

    @Operation(summary = "Unlink LINE from my account")
    @DeleteMapping("/line/link")
    public ResponseEntity<ApiResponse<Void>> unlink(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        lineLoginService.unlink(authHeader);
        return ResponseEntity.ok(ApiResponse.success(null, "Unlinked"));
    }
}
