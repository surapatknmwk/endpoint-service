package com.authen.service;

import com.authen.config.RegistrationProperties;
import com.authen.constant.SecurityEventType;
import com.authen.dto.LineCallbackResponse;
import com.authen.dto.LineLinkResponse;
import com.authen.dto.LineRegisterRequest;
import com.authen.dto.LoginResponse;
import com.authen.entity.LineLoginAttempt;
import com.authen.entity.Role;
import com.authen.entity.User;
import com.authen.entity.UserIdentity;
import com.authen.entity.UserRegistration;
import com.authen.exception.AuthApiException;
import com.authen.repository.LineLoginAttemptRepository;
import com.authen.repository.RoleRepository;
import com.authen.repository.UserIdentityRepository;
import com.authen.repository.UserRegistrationRepository;
import com.authen.repository.UserRepository;
import com.authen.util.JwtUtil;
import com.core.constant.AccountRole;
import com.master.service.FeatureFlagService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

/**
 * LINE Login: log in, sign up and link / unlink (register.md US-REG-07 … 10).
 * State, nonce and the PKCE verifier stay on the server ({@code line_login_attempts}); state and
 * ticket are stored hashed and are single-use.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LineLoginService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String OWNER = "OWNER";

    static final String MSG_EXPIRED = "การเข้าสู่ระบบหมดเวลา กรุณาลองใหม่อีกครั้ง";
    static final String MSG_TICKET_EXPIRED = "หมดเวลาสมัคร กรุณาเข้าสู่ระบบด้วย LINE อีกครั้ง";
    static final String MSG_LINE_TAKEN = "บัญชี LINE นี้เชื่อมกับบัญชีอื่นแล้ว";
    static final String MSG_USERNAME_TAKEN = "ชื่อผู้ใช้นี้ถูกใช้แล้ว";

    private final RegistrationProperties properties;
    private final LineOidcClient lineClient;
    private final LineLoginAttemptRepository attemptRepository;
    private final UserIdentityRepository identityRepository;
    private final UserRegistrationRepository registrationRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AuthenticationService authenticationService;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final SecurityEventRecorder securityEventRecorder;
    private final FeatureFlagService featureFlags;

    /** GET /auth/line/authorize — purpose LOGIN (public) or LINK (needs the caller's token). */
    @Transactional
    public String authorize(String purpose, String authHeader, HttpServletRequest request) {
        requireLineEnabled();
        String normalized = LineLoginAttempt.PURPOSE_LINK.equalsIgnoreCase(purpose)
                ? LineLoginAttempt.PURPOSE_LINK : LineLoginAttempt.PURPOSE_LOGIN;
        Integer linkUserId = LineLoginAttempt.PURPOSE_LINK.equals(normalized)
                ? currentUser(authHeader).getUserId() : null;

        String state = randomToken(32);
        String nonce = randomToken(24);
        String verifier = randomToken(48);
        LocalDateTime now = LocalDateTime.now();

        LineLoginAttempt attempt = new LineLoginAttempt();
        attempt.setStateHash(sha256(state));
        attempt.setNonce(nonce);
        attempt.setCodeVerifier(verifier);
        attempt.setPurpose(normalized);
        attempt.setLinkUserId(linkUserId);
        attempt.setExpiresAt(now.plusSeconds(properties.getAttemptTtlSeconds()));
        attempt.setIpAddress(clientIp(request));
        attempt.setCreatedAt(now);
        attemptRepository.save(attempt);

        return lineClient.authorizeUrl(state, nonce, codeChallenge(verifier));
    }

    /**
     * POST /auth/line/callback. Not one transaction with the LINE calls on purpose: the state is
     * consumed first (committed even when LINE then fails), so a code can never be replayed.
     */
    @Transactional(noRollbackFor = AuthApiException.class)
    public LineCallbackResponse callback(String code, String state, HttpServletRequest request) {
        requireLineEnabled();
        LocalDateTime now = LocalDateTime.now();
        LineLoginAttempt attempt = attemptRepository.findByStateHash(sha256(state)).orElse(null);
        if (attempt == null || attempt.getConsumedAt() != null || attempt.getExpiresAt().isBefore(now)) {
            fail(request, null, "state unknown, used or expired");
            throw new AuthApiException(HttpStatus.BAD_REQUEST, MSG_EXPIRED);
        }
        attempt.setConsumedAt(now);
        attemptRepository.save(attempt);

        LineOidcClient.LineProfile profile;
        try {
            profile = lineClient.exchangeAndVerify(code, attempt.getCodeVerifier(), attempt.getNonce());
        } catch (LineOidcClient.LineFailure ex) {
            fail(request, null, ex.getReason());
            throw ex;
        }
        attempt.setLineUserId(profile.getSub());
        attempt.setLineDisplayName(truncate(profile.getName(), 100));
        attempt.setLinePictureUrl(truncate(profile.getPicture(), 500));

        if (LineLoginAttempt.PURPOSE_LINK.equals(attempt.getPurpose())) {
            return link(attempt, request);
        }

        UserIdentity identity = identityRepository
                .findByProviderAndProviderUserId(UserIdentity.PROVIDER_LINE, profile.getSub()).orElse(null);
        if (identity != null) {
            User user = userRepository.findById(identity.getUserId())
                    .orElseThrow(() -> new AuthApiException(HttpStatus.BAD_REQUEST, MSG_EXPIRED));
            if (!Boolean.TRUE.equals(user.getIsActive())) {
                fail(request, user.getUsername(), "Account is disabled");
                throw new AuthApiException(HttpStatus.BAD_REQUEST, "บัญชีนี้ถูกระงับ กรุณาติดต่อผู้ดูแลระบบ");
            }
            identity.setDisplayName(attempt.getLineDisplayName());
            identity.setPictureUrl(attempt.getLinePictureUrl());
            identity.setLastLoginAt(now);
            identityRepository.save(identity);
            LoginResponse session = authenticationService.issueSession(user, request);
            return LineCallbackResponse.builder().result(LineCallbackResponse.LOGGED_IN).token(session.getToken()).build();
        }

        if (!isRegistrationEnabled()) {
            throw new AuthApiException(HttpStatus.FORBIDDEN,
                    "ยังไม่มีบัญชีที่เชื่อมกับ LINE นี้ และระบบปิดรับสมัครชั่วคราว");
        }
        String ticket = randomToken(32);
        attempt.setTicketHash(sha256(ticket));
        attempt.setTicketExpiresAt(now.plusSeconds(properties.getTicketTtlSeconds()));
        attemptRepository.save(attempt);
        return LineCallbackResponse.builder()
                .result(LineCallbackResponse.REGISTER)
                .registrationTicket(ticket)
                .expiresInSeconds(properties.getTicketTtlSeconds())
                .lineDisplayName(attempt.getLineDisplayName())
                .build();
    }

    /** POST /auth/register/line — creates the account and logs it in. */
    @Transactional
    public LoginResponse register(LineRegisterRequest body, HttpServletRequest request) {
        requireLineEnabled();
        if (!isRegistrationEnabled()) {
            throw new AuthApiException(HttpStatus.FORBIDDEN, "ระบบปิดรับสมัครสมาชิกชั่วคราว");
        }
        LocalDateTime now = LocalDateTime.now();
        LineLoginAttempt attempt = attemptRepository.findByTicketHash(sha256(body.getRegistrationTicket())).orElse(null);
        if (attempt == null || attempt.getTicketUsedAt() != null || attempt.getTicketExpiresAt() == null
                || attempt.getTicketExpiresAt().isBefore(now)) {
            throw new AuthApiException(HttpStatus.BAD_REQUEST, MSG_TICKET_EXPIRED);
        }
        if (identityRepository.findByProviderAndProviderUserId(UserIdentity.PROVIDER_LINE, attempt.getLineUserId()).isPresent()) {
            throw new AuthApiException(HttpStatus.BAD_REQUEST, MSG_LINE_TAKEN);
        }
        // checked before the ticket is used, so the visitor can pick another name with the same ticket
        if (!userRepository.findByUsernameIgnoreCase(body.getUsername()).isEmpty()) {
            throw new AuthApiException(HttpStatus.BAD_REQUEST, MSG_USERNAME_TAKEN, "username");
        }
        String ip = clientIp(request);
        if (registrationRepository.countByRegisteredIpAndCreatedAtAfter(ip, now.minusHours(1))
                >= properties.getMaxPerIpPerHour()) {
            securityEventRecorder.record(SecurityEventType.REGISTER_BLOCKED, request, body.getUsername(),
                    "More than " + properties.getMaxPerIpPerHour() + " accounts per hour");
            throw new AuthApiException(HttpStatus.TOO_MANY_REQUESTS, "สมัครสมาชิกบ่อยเกินไป กรุณาลองใหม่ภายหลัง");
        }

        String roleName = OWNER.equals(body.getAccountType()) ? properties.getDefaultRole() : AccountRole.DRIVER;
        Role role = roleRepository.findByRoleName(roleName)
                .orElseThrow(() -> new IllegalStateException("Role " + roleName + " is missing — run db/alter-add-line-login.sql"));

        User user = new User();
        user.setUsername(body.getUsername());
        // no password the visitor knows: an encoded random value, never kept in clear
        user.setPasswordHash(passwordEncoder.encode(randomToken(32)));
        user.setFirstName(body.getFirstName().trim());
        user.setLastName(StringUtils.hasText(body.getLastName()) ? body.getLastName().trim() : null);
        user.setPhone(body.getPhone());
        user.setRole(role);
        user.setIsActive(true);
        try {
            user = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            throw new AuthApiException(HttpStatus.BAD_REQUEST, MSG_USERNAME_TAKEN, "username");
        }

        UserRegistration registration = new UserRegistration();
        registration.setUserId(user.getUserId());
        registration.setSignupMethod(UserRegistration.METHOD_LINE);
        registration.setTermsVersion(properties.getTermsVersion());
        registration.setTermsAcceptedAt(now);
        registration.setRegisteredIp(ip);
        registration.setCreatedAt(now);
        registrationRepository.save(registration);

        try {
            identityRepository.saveAndFlush(newIdentity(user.getUserId(), attempt, now));
        } catch (DataIntegrityViolationException ex) {
            throw new AuthApiException(HttpStatus.BAD_REQUEST, MSG_LINE_TAKEN);
        }

        attempt.setTicketUsedAt(now);
        attemptRepository.save(attempt);
        return authenticationService.issueSession(user, request);
    }

    /** GET /auth/line/link */
    @Transactional(readOnly = true)
    public LineLinkResponse linkStatus(String authHeader) {
        User user = currentUser(authHeader);
        return identityRepository.findByUserIdAndProvider(user.getUserId(), UserIdentity.PROVIDER_LINE)
                .map(identity -> linkedResponse(identity, user.getUserId()))
                .orElse(LineLinkResponse.builder().linked(false).canUnlink(false).build());
    }

    /** PATCH /auth/line/notify — the "แจ้งเตือนทาง LINE" switch (design lineNotify L2) */
    @Transactional
    public LineLinkResponse setNotify(String authHeader, boolean enabled) {
        User user = currentUser(authHeader);
        if (!properties.isLineMessagingEnabled()) {
            throw new AuthApiException(HttpStatus.FORBIDDEN, "ปิดการแจ้งเตือนทาง LINE ชั่วคราว");
        }
        UserIdentity identity = identityRepository.findByUserIdAndProvider(user.getUserId(), UserIdentity.PROVIDER_LINE)
                .orElseThrow(() -> new AuthApiException(HttpStatus.BAD_REQUEST, "บัญชีนี้ยังไม่ได้เชื่อม LINE"));
        identity.setNotifyEnabled(enabled);
        return linkedResponse(identityRepository.save(identity), user.getUserId());
    }

    private LineLinkResponse linkedResponse(UserIdentity identity, Integer userId) {
        boolean messaging = properties.isLineMessagingEnabled();
        return LineLinkResponse.builder()
                .linked(true)
                .displayName(identity.getDisplayName())
                .linkedAt(identity.getLinkedAt())
                .canUnlink(!isLineOnly(userId))
                .notifyEnabled(messaging ? identity.isNotifyEnabled() : null)
                .addFriendUrl(messaging && StringUtils.hasText(properties.getLineAddFriendUrl())
                        ? properties.getLineAddFriendUrl() : null)
                .build();
    }

    /** DELETE /auth/line/link */
    @Transactional
    public void unlink(String authHeader) {
        User user = currentUser(authHeader);
        UserIdentity identity = identityRepository.findByUserIdAndProvider(user.getUserId(), UserIdentity.PROVIDER_LINE)
                .orElseThrow(() -> new AuthApiException(HttpStatus.BAD_REQUEST, "บัญชีนี้ยังไม่ได้เชื่อม LINE"));
        if (isLineOnly(user.getUserId())) {
            throw new AuthApiException(HttpStatus.BAD_REQUEST,
                    "ยกเลิกการเชื่อมไม่ได้ บัญชีนี้เข้าสู่ระบบด้วย LINE อย่างเดียว");
        }
        identityRepository.delete(identity);
    }

    /** LINE log-in = application.yml line.login.enabled AND feature flag FF_LINE (configuration table). */
    public boolean isLineEnabled() {
        return properties.isLineEnabled() && featureFlags.isEnabled(FeatureFlagService.LINE_LOGIN);
    }

    /** Sign-up = application.yml registration.enabled AND feature flag FF_REG (configuration table). */
    public boolean isRegistrationEnabled() {
        return properties.isRegistrationEnabled() && featureFlags.isEnabled(FeatureFlagService.REGISTER);
    }

    private LineCallbackResponse link(LineLoginAttempt attempt, HttpServletRequest request) {
        Integer userId = attempt.getLinkUserId();
        LocalDateTime now = LocalDateTime.now();
        UserIdentity byLine = identityRepository
                .findByProviderAndProviderUserId(UserIdentity.PROVIDER_LINE, attempt.getLineUserId()).orElse(null);
        if (byLine != null && !byLine.getUserId().equals(userId)) {
            fail(request, null, "LINE already linked to another user");
            throw new AuthApiException(HttpStatus.BAD_REQUEST, MSG_LINE_TAKEN);
        }
        UserIdentity identity = byLine;
        if (identity == null) {
            if (identityRepository.findByUserIdAndProvider(userId, UserIdentity.PROVIDER_LINE).isPresent()) {
                throw new AuthApiException(HttpStatus.BAD_REQUEST,
                        "บัญชีนี้เชื่อมกับ LINE อื่นอยู่แล้ว ยกเลิกการเชื่อมก่อน");
            }
            identity = newIdentity(userId, attempt, now);
        } else {
            identity.setDisplayName(attempt.getLineDisplayName());
            identity.setPictureUrl(attempt.getLinePictureUrl());
        }
        try {
            identity = identityRepository.saveAndFlush(identity);
        } catch (DataIntegrityViolationException ex) {
            throw new AuthApiException(HttpStatus.BAD_REQUEST, MSG_LINE_TAKEN);
        }
        return LineCallbackResponse.builder()
                .result(LineCallbackResponse.LINKED)
                .line(linkedResponse(identity, userId))
                .build();
    }

    private UserIdentity newIdentity(Integer userId, LineLoginAttempt attempt, LocalDateTime now) {
        UserIdentity identity = new UserIdentity();
        identity.setUserId(userId);
        identity.setProvider(UserIdentity.PROVIDER_LINE);
        identity.setProviderUserId(attempt.getLineUserId());
        identity.setDisplayName(attempt.getLineDisplayName());
        identity.setPictureUrl(attempt.getLinePictureUrl());
        identity.setLinkedAt(now);
        return identity;
    }

    private boolean isLineOnly(Integer userId) {
        return registrationRepository.findById(userId)
                .map(r -> UserRegistration.METHOD_LINE.equals(r.getSignupMethod()))
                .orElse(false);
    }

    /** /auth/** is public in SecurityConfig, so endpoints that need a user check the Bearer token here. */
    private User currentUser(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new AuthApiException(HttpStatus.UNAUTHORIZED, "กรุณาเข้าสู่ระบบ");
        }
        String token = authHeader.substring("Bearer ".length());
        if (!authenticationService.validateToken(token)) {
            throw new AuthApiException(HttpStatus.UNAUTHORIZED, "กรุณาเข้าสู่ระบบ");
        }
        return userRepository.findByUsername(jwtUtil.extractUsername(token))
                .orElseThrow(() -> new AuthApiException(HttpStatus.UNAUTHORIZED, "กรุณาเข้าสู่ระบบ"));
    }

    private void requireLineEnabled() {
        if (!isLineEnabled()) {
            throw new AuthApiException(HttpStatus.FORBIDDEN, "ปิดการเข้าสู่ระบบด้วย LINE ชั่วคราว");
        }
    }

    private void fail(HttpServletRequest request, String username, String reason) {
        securityEventRecorder.record(SecurityEventType.LINE_LOGIN_FAILED, request, username, reason);
    }

    private static String randomToken(int bytes) {
        byte[] buffer = new byte[bytes];
        RANDOM.nextBytes(buffer);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer);
    }

    private static String codeChallenge(String verifier) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest(verifier));
    }

    private static String sha256(String value) {
        return HexFormat.of().formatHex(digest(value));
    }

    private static byte[] digest(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.US_ASCII));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isEmpty()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        return realIp != null && !realIp.isEmpty() ? realIp : request.getRemoteAddr();
    }
}
