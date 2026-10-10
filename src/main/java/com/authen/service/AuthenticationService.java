package com.authen.service;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.authen.constant.SecurityEventType;
import com.authen.dto.LoginRequest;
import com.authen.dto.LoginResponse;
import com.authen.entity.User;
import com.authen.entity.UserSession;
import com.authen.repository.PermissionRepository;
import com.authen.repository.UserRepository;
import com.authen.repository.UserSessionRepository;
import com.authen.util.JwtUtil;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final UserRepository userRepository;
    private final UserSessionRepository userSessionRepository;
    private final PermissionRepository permissionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final SecurityEventRecorder securityEventRecorder;

    @Value("${jwt.expiration}")
    private Long jwtExpiration;

    @Transactional
    public LoginResponse login(LoginRequest request, HttpServletRequest httpRequest) {

        log.info("login : username={}", request.getUsername());

        User user = userRepository.findByUsername(request.getUsername()).orElse(null);
        if (user == null) {
            recordLoginFailed(request, httpRequest, "Unknown username");
            throw new RuntimeException("Invalid username or password");
        }

        if (!user.getIsActive()) {
            recordLoginFailed(request, httpRequest, "Account is disabled");
            throw new RuntimeException("User account is disabled");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            recordLoginFailed(request, httpRequest, "Wrong password");
            throw new RuntimeException("Invalid username or password");
        }

        // legacy plain-text row: encrypt it now, saved together with last_login below
        if (passwordEncoder.upgradeEncoding(user.getPasswordHash())) {
            user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        }

        String token = jwtUtil.generateToken(
                user.getUsername(),
                user.getUserId(),
                user.getRole().getRoleName()
        );

        UserSession session = new UserSession();
        session.setUser(user);
        session.setToken(token);
        session.setIpAddress(getClientIp(httpRequest));
        session.setUserAgent(httpRequest.getHeader("User-Agent"));
        session.setExpiresAt(LocalDateTime.now().plusSeconds(jwtExpiration / 1000));
        userSessionRepository.save(session);

        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);

        return LoginResponse.builder()
                .token(token)
                .build();
    }

    @Transactional
    public void logout(String token) {
        userSessionRepository.findByToken(token)
                .ifPresent(userSessionRepository::delete);
    }

    public boolean validateToken(String token) {
        try {
            UserSession session = userSessionRepository.findByToken(token)
                    .orElse(null);

            log.info("Validate Token session : sessionId={}", session != null ? session.getSessionId() : null);

            if (session == null) {
                return false;
            }

            if (session.getExpiresAt().isBefore(LocalDateTime.now())) {
                userSessionRepository.delete(session);
                return false;
            }

            String username = jwtUtil.extractUsername(token);
            return jwtUtil.validateToken(token, username);
        } catch (Exception e) {
            return false;
        }
    }

    public boolean checkPermission(String token, String method, String api) {
        log.info("checkPermission : method={} ,api={}", method, api);

        if (!validateToken(token)) {
            throw new RuntimeException("Invalid or expired token");
        }

        String roleName = jwtUtil.extractRole(token);
        return permissionRepository.existsByRoleNameAndMethodAndApi(roleName, method, api);
    }

    @Transactional
    public LoginResponse refreshToken(String token, HttpServletRequest httpRequest) {
        UserSession existingSession = userSessionRepository.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Invalid or expired token"));

        if (existingSession.getExpiresAt().isBefore(LocalDateTime.now())) {
            userSessionRepository.delete(existingSession);
            throw new RuntimeException("Token has expired");
        }

        User user = existingSession.getUser();
        if (!user.getIsActive()) {
            throw new RuntimeException("User account is disabled");
        }

        String newToken = jwtUtil.generateToken(
                user.getUsername(),
                user.getUserId(),
                user.getRole().getRoleName()
        );

        existingSession.setToken(newToken);
        existingSession.setIpAddress(getClientIp(httpRequest));
        existingSession.setUserAgent(httpRequest.getHeader("User-Agent"));
        existingSession.setExpiresAt(LocalDateTime.now().plusSeconds(jwtExpiration / 1000));
        userSessionRepository.save(existingSession);

        return LoginResponse.builder()
                .token(newToken)
                .build();
    }

    private void recordLoginFailed(LoginRequest request, HttpServletRequest httpRequest, String reason) {
        securityEventRecorder.record(SecurityEventType.LOGIN_FAILED, httpRequest, request.getUsername(), reason);
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        return request.getRemoteAddr();
    }

}
