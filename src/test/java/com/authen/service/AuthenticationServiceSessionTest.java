package com.authen.service;

import com.authen.dto.LoginRequest;
import com.authen.dto.LoginResponse;
import com.authen.entity.Role;
import com.authen.entity.User;
import com.authen.entity.UserSession;
import com.authen.repository.PermissionRepository;
import com.authen.repository.UserRepository;
import com.authen.repository.UserSessionRepository;
import com.authen.util.AesPasswordEncoder;
import com.authen.util.JwtUtil;
import com.common.exception.SessionReplacedException;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.context.ApplicationEventPublisher;
import com.authen.event.UserLoggedInEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Covers the session side of {@link AuthenticationService}: what login stores, token validation
 * against {@code user_sessions}, permission lookup, refresh and logout. Expiry is tested with
 * dates far from now because the service reads the system clock directly.
 */
@ExtendWith(MockitoExtension.class)
class AuthenticationServiceSessionTest {

    private static final long ONE_HOUR_MS = 3_600_000L;
    private static final String PASSWORD = "S3cret!pass";

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserSessionRepository userSessionRepository;
    @Mock
    private PermissionRepository permissionRepository;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private SecurityEventRecorder securityEventRecorder;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private final AesPasswordEncoder passwordEncoder =
            new AesPasswordEncoder(Base64.getEncoder().encodeToString(new byte[32]));

    private AuthenticationService service;

    @BeforeEach
    void setUp() {
        service = new AuthenticationService(
                userRepository, userSessionRepository, permissionRepository, passwordEncoder, jwtUtil,
                securityEventRecorder, eventPublisher);
        ReflectionTestUtils.setField(service, "jwtExpiration", ONE_HOUR_MS);
    }

    private User user(boolean active) {
        Role role = new Role();
        role.setRoleName("USER_VIP_0");

        User user = new User();
        user.setUserId(1);
        user.setUsername("admin");
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setRole(role);
        user.setIsActive(active);
        return user;
    }

    private UserSession session(String token, LocalDateTime expiresAt, User user) {
        UserSession session = new UserSession();
        session.setSessionId(5);
        session.setToken(token);
        session.setExpiresAt(expiresAt);
        session.setUser(user);
        return session;
    }

    // ---------- login: what gets stored ----------

    @Test
    void should_storeSessionWithClientDetailsAndStampLastLogin_when_loginSucceeds() {
        //GIVEN
        User user = user(true);
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.addHeader("X-Forwarded-For", "203.0.113.9, 10.0.0.1");
        httpRequest.addHeader("User-Agent", "Mozilla/5.0");
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));
        when(jwtUtil.generateToken("admin", 1, "USER_VIP_0")).thenReturn("jwt-token");
        LocalDateTime before = LocalDateTime.now();

        //WHEN
        LoginResponse response = service.login(new LoginRequest("admin", PASSWORD), httpRequest);

        //THEN
        ArgumentCaptor<UserSession> saved = ArgumentCaptor.forClass(UserSession.class);
        verify(userSessionRepository).save(saved.capture());
        assertThat(response.getToken()).isEqualTo("jwt-token");
        assertThat(saved.getValue().getToken()).isEqualTo("jwt-token");
        assertThat(saved.getValue().getUser()).isSameAs(user);
        assertThat(saved.getValue().getIpAddress()).isEqualTo("203.0.113.9");
        assertThat(saved.getValue().getUserAgent()).isEqualTo("Mozilla/5.0");
        assertThat(saved.getValue().getExpiresAt())
                .isBetween(before.plusHours(1).minusSeconds(1), LocalDateTime.now().plusHours(1));
        assertThat(user.getLastLogin()).isNotNull();
        verify(userRepository).save(user);
    }

    @Test
    void should_markOtherSessionsReplacedBeforeStoringNewOne_when_loginSucceeds() {
        //GIVEN - the account is already logged in on 2 other devices
        User user = user(true);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));
        when(jwtUtil.generateToken("admin", 1, "USER_VIP_0")).thenReturn("jwt-token");
        when(userSessionRepository.endActiveSessions(1, UserSession.ENDED_REPLACED)).thenReturn(2);

        //WHEN
        service.login(new LoginRequest("admin", PASSWORD), new MockHttpServletRequest());

        //THEN - old sessions ended first, so only the new token stays valid
        InOrder order = inOrder(userSessionRepository);
        order.verify(userSessionRepository).endActiveSessions(1, UserSession.ENDED_REPLACED);
        order.verify(userSessionRepository).save(any(UserSession.class));
    }

    @Test
    void should_announceLoginWithDeviceIdAndClientDetails_when_loginSucceeds() {
        //GIVEN — driver-risk ข้อ 15: DriverDeviceWatcher listens to this event
        User user = user(true);
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.addHeader("X-Device-Id", "3f2b8c1e-device");
        httpRequest.addHeader("User-Agent", "Mozilla/5.0 (iPhone)");
        httpRequest.setRemoteAddr("198.51.100.4");
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));
        when(jwtUtil.generateToken("admin", 1, "USER_VIP_0")).thenReturn("jwt-token");

        //WHEN
        service.login(new LoginRequest("admin", PASSWORD), httpRequest);

        //THEN
        verify(eventPublisher).publishEvent(new UserLoggedInEvent(user, "3f2b8c1e-device", "Mozilla/5.0 (iPhone)",
                "198.51.100.4"));
    }

    @Test
    void should_useRealIpHeaderThenRemoteAddress_when_noForwardedForHeader() {
        //GIVEN
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user(true)));
        when(jwtUtil.generateToken("admin", 1, "USER_VIP_0")).thenReturn("jwt-1", "jwt-2");
        MockHttpServletRequest withRealIp = new MockHttpServletRequest();
        withRealIp.addHeader("X-Real-IP", "198.51.100.4");
        MockHttpServletRequest direct = new MockHttpServletRequest();
        direct.setRemoteAddr("192.0.2.77");

        //WHEN
        service.login(new LoginRequest("admin", PASSWORD), withRealIp);
        service.login(new LoginRequest("admin", PASSWORD), direct);

        //THEN
        ArgumentCaptor<UserSession> saved = ArgumentCaptor.forClass(UserSession.class);
        verify(userSessionRepository, org.mockito.Mockito.times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(UserSession::getIpAddress)
                .containsExactly("198.51.100.4", "192.0.2.77");
    }

    // ---------- validateToken ----------

    @Test
    void should_returnFalse_when_tokenHasNoSession() {
        //GIVEN - e.g. after logout, or a token that was never issued here
        when(userSessionRepository.findByToken("unknown")).thenReturn(Optional.empty());

        //WHEN
        boolean valid = service.validateToken("unknown");

        //THEN
        assertThat(valid).isFalse();
        verifyNoInteractions(jwtUtil);
    }

    @Test
    void should_returnFalseAndDeleteSession_when_sessionHasExpired() {
        //GIVEN
        UserSession expired = session("jwt-token", LocalDateTime.now().minusDays(1), user(true));
        when(userSessionRepository.findByToken("jwt-token")).thenReturn(Optional.of(expired));

        //WHEN
        boolean valid = service.validateToken("jwt-token");

        //THEN
        assertThat(valid).isFalse();
        verify(userSessionRepository).delete(expired);
        verifyNoInteractions(jwtUtil);
    }

    @Test
    void should_returnTrue_when_sessionIsLiveAndTokenSignatureIsValid() {
        //GIVEN
        UserSession live = session("jwt-token", LocalDateTime.now().plusDays(1), user(true));
        when(userSessionRepository.findByToken("jwt-token")).thenReturn(Optional.of(live));
        when(jwtUtil.extractUsername("jwt-token")).thenReturn("admin");
        when(jwtUtil.validateToken("jwt-token", "admin")).thenReturn(true);

        //WHEN
        boolean valid = service.validateToken("jwt-token");

        //THEN
        assertThat(valid).isTrue();
        verify(userSessionRepository, never()).delete(any());
    }

    @Test
    void should_returnFalseAndKeepSession_when_userWasDisabledAfterLogin() {
        //GIVEN - live session, signature fine, but the account was switched off by SQL
        UserSession live = session("jwt-token", LocalDateTime.now().plusDays(1), user(false));
        when(userSessionRepository.findByToken("jwt-token")).thenReturn(Optional.of(live));

        //WHEN
        boolean valid = service.validateToken("jwt-token");

        //THEN
        assertThat(valid).isFalse();
        verify(userSessionRepository, never()).delete(any());
        verifyNoInteractions(jwtUtil);
    }

    @Test
    void should_returnFalse_when_tokenFailsJwtValidation() {
        //GIVEN
        UserSession live = session("jwt-token", LocalDateTime.now().plusDays(1), user(true));
        when(userSessionRepository.findByToken("jwt-token")).thenReturn(Optional.of(live));
        when(jwtUtil.extractUsername("jwt-token")).thenReturn("admin");
        when(jwtUtil.validateToken("jwt-token", "admin")).thenReturn(false);

        //WHEN
        boolean valid = service.validateToken("jwt-token");

        //THEN
        assertThat(valid).isFalse();
    }

    @Test
    void should_returnFalse_when_tokenSignatureCannotBeVerified() {
        //GIVEN
        UserSession live = session("forged", LocalDateTime.now().plusDays(1), user(true));
        when(userSessionRepository.findByToken("forged")).thenReturn(Optional.of(live));
        when(jwtUtil.extractUsername("forged")).thenThrow(new SignatureException("bad signature"));

        //WHEN
        boolean valid = service.validateToken("forged");

        //THEN
        assertThat(valid).isFalse();
    }

    @Test
    void should_returnFalse_when_sessionLookupFails() {
        //GIVEN
        when(userSessionRepository.findByToken("jwt-token")).thenThrow(new IllegalStateException("DB unreachable"));

        //WHEN
        boolean valid = service.validateToken("jwt-token");

        //THEN
        assertThat(valid).isFalse();
    }

    // ---------- checkPermission ----------

    @Test
    void should_returnPermissionOfTokensRole_when_tokenIsValid() {
        //GIVEN
        UserSession live = session("jwt-token", LocalDateTime.now().plusDays(1), user(true));
        when(userSessionRepository.findByToken("jwt-token")).thenReturn(Optional.of(live));
        when(jwtUtil.extractUsername("jwt-token")).thenReturn("admin");
        when(jwtUtil.validateToken("jwt-token", "admin")).thenReturn(true);
        when(jwtUtil.extractRole("jwt-token")).thenReturn("USER_VIP_0");
        when(permissionRepository.existsByRoleNameAndMethodAndApi("USER_VIP_0", "GET", "/api/jobs")).thenReturn(true);
        when(permissionRepository.existsByRoleNameAndMethodAndApi("USER_VIP_0", "DELETE", "/api/jobs/{jobId}")).thenReturn(false);

        //WHEN
        boolean allowed = service.checkPermission("jwt-token", "GET", "/api/jobs");
        boolean denied = service.checkPermission("jwt-token", "DELETE", "/api/jobs/{jobId}");

        //THEN
        assertThat(allowed).isTrue();
        assertThat(denied).isFalse();
    }

    @Test
    void should_throwWithoutLookingUpPermission_when_tokenIsInvalid() {
        //GIVEN
        when(userSessionRepository.findByToken("unknown")).thenReturn(Optional.empty());

        //WHEN / THEN
        assertThatThrownBy(() -> service.checkPermission("unknown", "GET", "/api/jobs"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Invalid or expired token");
        verifyNoInteractions(permissionRepository);
    }

    // ---------- refreshToken ----------

    @Test
    void should_replaceTokenAndExtendSession_when_refreshingLiveSession() {
        //GIVEN
        UserSession live = session("old-token", LocalDateTime.now().plusMinutes(5), user(true));
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.addHeader("X-Forwarded-For", "203.0.113.9");
        httpRequest.addHeader("User-Agent", "Mozilla/5.0");
        when(userSessionRepository.findByToken("old-token")).thenReturn(Optional.of(live));
        when(jwtUtil.generateToken("admin", 1, "USER_VIP_0")).thenReturn("new-token");
        LocalDateTime before = LocalDateTime.now();

        //WHEN
        LoginResponse response = service.refreshToken("old-token", httpRequest);

        //THEN - same session row, so the old token stops working
        assertThat(response.getToken()).isEqualTo("new-token");
        assertThat(live.getToken()).isEqualTo("new-token");
        assertThat(live.getIpAddress()).isEqualTo("203.0.113.9");
        assertThat(live.getUserAgent()).isEqualTo("Mozilla/5.0");
        assertThat(live.getExpiresAt()).isAfterOrEqualTo(before.plusHours(1).minusSeconds(1));
        verify(userSessionRepository).save(live);
    }

    @Test
    void should_throw_when_refreshingUnknownToken() {
        //GIVEN
        when(userSessionRepository.findByToken("unknown")).thenReturn(Optional.empty());

        //WHEN / THEN
        assertThatThrownBy(() -> service.refreshToken("unknown", new MockHttpServletRequest()))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Invalid or expired token");
        verifyNoInteractions(jwtUtil);
    }

    @Test
    void should_deleteSessionAndThrow_when_refreshingExpiredSession() {
        //GIVEN
        UserSession expired = session("old-token", LocalDateTime.now().minusDays(1), user(true));
        when(userSessionRepository.findByToken("old-token")).thenReturn(Optional.of(expired));

        //WHEN / THEN
        assertThatThrownBy(() -> service.refreshToken("old-token", new MockHttpServletRequest()))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Token has expired");
        verify(userSessionRepository).delete(expired);
        verify(userSessionRepository, never()).save(any());
        verifyNoInteractions(jwtUtil);
    }

    @Test
    void should_throwAndKeepOldToken_when_refreshingSessionOfDisabledUser() {
        //GIVEN
        UserSession live = session("old-token", LocalDateTime.now().plusMinutes(5), user(false));
        when(userSessionRepository.findByToken("old-token")).thenReturn(Optional.of(live));

        //WHEN / THEN
        assertThatThrownBy(() -> service.refreshToken("old-token", new MockHttpServletRequest()))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("User account is disabled");
        assertThat(live.getToken()).isEqualTo("old-token");
        verify(userSessionRepository, never()).save(any());
        verifyNoInteractions(jwtUtil);
    }

    // ---------- one session per account: replaced sessions ----------

    @Test
    void should_returnFalseAndKeepRow_when_sessionWasReplacedByAnotherLogin() {
        //GIVEN - still within its hour, but another device logged in
        UserSession replaced = session("old-token", LocalDateTime.now().plusMinutes(30), user(true));
        replaced.setEndedReason(UserSession.ENDED_REPLACED);
        when(userSessionRepository.findByToken("old-token")).thenReturn(Optional.of(replaced));

        //WHEN
        boolean valid = service.validateToken("old-token");

        //THEN - row kept so the filter can answer SESSION_REPLACED
        assertThat(valid).isFalse();
        assertThat(service.isSessionReplaced("old-token")).isTrue();
        verify(userSessionRepository, never()).delete(any());
    }

    @Test
    void should_notReportReplaced_when_tokenUnknownOrSessionActive() {
        //GIVEN
        when(userSessionRepository.findByToken("unknown")).thenReturn(Optional.empty());
        when(userSessionRepository.findByToken("live")).thenReturn(Optional.of(
                session("live", LocalDateTime.now().plusMinutes(30), user(true))));

        //WHEN / THEN
        assertThat(service.isSessionReplaced("unknown")).isFalse();
        assertThat(service.isSessionReplaced("live")).isFalse();
    }

    @Test
    void should_throwSessionReplaced_when_refreshingReplacedSession() {
        //GIVEN
        UserSession replaced = session("old-token", LocalDateTime.now().plusMinutes(30), user(true));
        replaced.setEndedReason(UserSession.ENDED_REPLACED);
        when(userSessionRepository.findByToken("old-token")).thenReturn(Optional.of(replaced));

        //WHEN / THEN
        assertThatThrownBy(() -> service.refreshToken("old-token", new MockHttpServletRequest()))
                .isInstanceOf(SessionReplacedException.class);
        verify(userSessionRepository, never()).save(any());
        verifyNoInteractions(jwtUtil);
    }

    // ---------- refreshToken: absolute session lifetime (jwt.session-max-age-days) ----------

    private UserSession liveSessionLoggedIn(LocalDateTime loggedInAt) {
        UserSession live = session("old-token", LocalDateTime.now().plusMinutes(5), user(true));
        live.setCreatedAt(loggedInAt);
        return live;
    }

    @Test
    void should_deleteSessionAndThrow_when_refreshingSessionOlderThanMaxAge() {
        //GIVEN - max 7 days, logged in 8 days ago
        ReflectionTestUtils.setField(service, "sessionMaxAgeDays", 7L);
        UserSession old = liveSessionLoggedIn(LocalDateTime.now().minusDays(8));
        when(userSessionRepository.findByToken("old-token")).thenReturn(Optional.of(old));

        //WHEN / THEN
        assertThatThrownBy(() -> service.refreshToken("old-token", new MockHttpServletRequest()))
                .isInstanceOf(RuntimeException.class)
                .hasMessage(AuthenticationService.MSG_SESSION_TOO_OLD);
        verify(userSessionRepository).delete(old);
        verify(userSessionRepository, never()).save(any());
        verifyNoInteractions(jwtUtil);
    }

    @Test
    void should_capExpiryAtMaxAge_when_refreshingInLastHourBeforeMaxAge() {
        //GIVEN - max 7 days, the limit is 30 minutes away (less than one token lifetime)
        ReflectionTestUtils.setField(service, "sessionMaxAgeDays", 7L);
        LocalDateTime loggedInAt = LocalDateTime.now().minusDays(7).plusMinutes(30);
        UserSession live = liveSessionLoggedIn(loggedInAt);
        when(userSessionRepository.findByToken("old-token")).thenReturn(Optional.of(live));
        when(jwtUtil.generateToken("admin", 1, "USER_VIP_0")).thenReturn("new-token");

        //WHEN
        service.refreshToken("old-token", new MockHttpServletRequest());

        //THEN - new token, but the session ends at the limit, not one hour from now
        assertThat(live.getToken()).isEqualTo("new-token");
        assertThat(live.getExpiresAt()).isEqualTo(loggedInAt.plusDays(7));
    }

    @Test
    void should_extendFullHour_when_refreshingSessionWellWithinMaxAge() {
        //GIVEN - max 7 days, logged in 1 day ago
        ReflectionTestUtils.setField(service, "sessionMaxAgeDays", 7L);
        UserSession live = liveSessionLoggedIn(LocalDateTime.now().minusDays(1));
        when(userSessionRepository.findByToken("old-token")).thenReturn(Optional.of(live));
        when(jwtUtil.generateToken("admin", 1, "USER_VIP_0")).thenReturn("new-token");
        LocalDateTime before = LocalDateTime.now();

        //WHEN
        service.refreshToken("old-token", new MockHttpServletRequest());

        //THEN
        assertThat(live.getExpiresAt()).isAfterOrEqualTo(before.plusHours(1).minusSeconds(1));
    }

    @Test
    void should_refreshWithoutLimit_when_maxAgeSetToZero() {
        //GIVEN - limit switched off, session a year old
        ReflectionTestUtils.setField(service, "sessionMaxAgeDays", 0L);
        UserSession live = liveSessionLoggedIn(LocalDateTime.now().minusDays(365));
        when(userSessionRepository.findByToken("old-token")).thenReturn(Optional.of(live));
        when(jwtUtil.generateToken("admin", 1, "USER_VIP_0")).thenReturn("new-token");

        //WHEN
        LoginResponse response = service.refreshToken("old-token", new MockHttpServletRequest());

        //THEN
        assertThat(response.getToken()).isEqualTo("new-token");
        verify(userSessionRepository, never()).delete(any());
    }

    // ---------- logout ----------

    @Test
    void should_deleteSession_when_loggingOutWithKnownToken() {
        //GIVEN
        UserSession live = session("jwt-token", LocalDateTime.now().plusDays(1), user(true));
        when(userSessionRepository.findByToken("jwt-token")).thenReturn(Optional.of(live));

        //WHEN
        service.logout("jwt-token");

        //THEN
        verify(userSessionRepository).delete(live);
    }

    @Test
    void should_doNothing_when_loggingOutWithUnknownToken() {
        //GIVEN
        when(userSessionRepository.findByToken("unknown")).thenReturn(Optional.empty());

        //WHEN
        service.logout("unknown");

        //THEN
        verify(userSessionRepository, never()).delete(any());
    }
}
