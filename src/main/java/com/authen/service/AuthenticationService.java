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
import com.authen.event.UserLoggedInEvent;
import org.springframework.context.ApplicationEventPublisher;
import com.authen.repository.PermissionRepository;
import com.authen.repository.UserRepository;
import com.authen.repository.UserSessionRepository;
import com.authen.util.JwtUtil;
import com.common.exception.SessionReplacedException;

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
    private final ApplicationEventPublisher eventPublisher;

    /** Browser-generated device id sent by the webapp on every request (localStorage "ep-device-id"). */
    public static final String DEVICE_ID_HEADER = "X-Device-Id";

    static final String MSG_SESSION_TOO_OLD = "Session is too old, please log in again";

    @Value("${jwt.expiration}")
    private Long jwtExpiration;

    /** Max days from log-in that refresh may keep a session alive; 0 or less = no limit. */
    @Value("${jwt.session-max-age-days:7}")
    private long sessionMaxAgeDays;

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

        return issueSession(user, httpRequest);
    }

    /**
     * Starts a session for an already authenticated user: JWT + {@code user_sessions} row +
     * {@code last_login}. Used by the password login and by LINE Login (register.md US-REG-08 / 09).
     */
    @Transactional
    public LoginResponse issueSession(User user, HttpServletRequest httpRequest) {
        String token = jwtUtil.generateToken(
                user.getUsername(),
                user.getUserId(),
                user.getRole().getRoleName()
        );

        // one session per account: logging in here signs the user out everywhere else
        int ended = userSessionRepository.endActiveSessions(user.getUserId(), UserSession.ENDED_REPLACED);
        if (ended > 0) {
            log.info("issueSession : ended {} other session(s) of username={}", ended, user.getUsername());
        }

        UserSession session = new UserSession();
        session.setUser(user);
        session.setToken(token);
        session.setIpAddress(getClientIp(httpRequest));
        session.setUserAgent(httpRequest.getHeader("User-Agent"));
        session.setExpiresAt(LocalDateTime.now().plusSeconds(jwtExpiration / 1000));
        userSessionRepository.save(session);

        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);
        // เครื่องที่ใช้ล็อกอิน (driver-risk ข้อ 15) — core.service.team.DriverDeviceWatcher
        eventPublisher.publishEvent(new UserLoggedInEvent(user, httpRequest.getHeader(DEVICE_ID_HEADER),
                session.getUserAgent(), session.getIpAddress()));

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

            // ended by a log-in on another device — row kept so isSessionReplaced() can tell the caller why
            if (session.getEndedReason() != null) {
                return false;
            }

            if (session.getExpiresAt().isBefore(LocalDateTime.now())) {
                userSessionRepository.delete(session);
                return false;
            }

            // a disabled account loses its live sessions at once, not when the token expires
            if (!Boolean.TRUE.equals(session.getUser().getIsActive())) {
                return false;
            }

            String username = jwtUtil.extractUsername(token);
            return jwtUtil.validateToken(token, username);
        } catch (Exception e) {
            return false;
        }
    }

    /** True when the token's session was ended by a log-in on another device (one session per account). */
    public boolean isSessionReplaced(String token) {
        return userSessionRepository.findByToken(token)
                .map(s -> UserSession.ENDED_REPLACED.equals(s.getEndedReason()))
                .orElse(false);
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

        if (existingSession.getEndedReason() != null) {
            throw new SessionReplacedException();
        }

        if (existingSession.getExpiresAt().isBefore(LocalDateTime.now())) {
            userSessionRepository.delete(existingSession);
            throw new RuntimeException("Token has expired");
        }

        User user = existingSession.getUser();
        if (!user.getIsActive()) {
            throw new RuntimeException("User account is disabled");
        }

        // absolute session lifetime: refresh can extend a session only until login time + max age,
        // then the user has to log in again (limits how long a stolen token can be kept alive)
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime maxExpiresAt = sessionMaxExpiresAt(existingSession);
        if (maxExpiresAt != null && !now.isBefore(maxExpiresAt)) {
            userSessionRepository.delete(existingSession);
            throw new RuntimeException(MSG_SESSION_TOO_OLD);
        }
        LocalDateTime expiresAt = now.plusSeconds(jwtExpiration / 1000);
        if (maxExpiresAt != null && expiresAt.isAfter(maxExpiresAt)) {
            expiresAt = maxExpiresAt;
        }

        String newToken = jwtUtil.generateToken(
                user.getUsername(),
                user.getUserId(),
                user.getRole().getRoleName()
        );

        existingSession.setToken(newToken);
        existingSession.setIpAddress(getClientIp(httpRequest));
        existingSession.setUserAgent(httpRequest.getHeader("User-Agent"));
        existingSession.setExpiresAt(expiresAt);
        userSessionRepository.save(existingSession);

        return LoginResponse.builder()
                .token(newToken)
                .build();
    }

    /** login time ({@code created_at}, kept across refreshes) + max age; null = no limit (setting <= 0 or old row). */
    private LocalDateTime sessionMaxExpiresAt(UserSession session) {
        if (sessionMaxAgeDays <= 0 || session.getCreatedAt() == null) {
            return null;
        }
        return session.getCreatedAt().plusDays(sessionMaxAgeDays);
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
