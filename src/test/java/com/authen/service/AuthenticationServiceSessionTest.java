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
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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

    private final AesPasswordEncoder passwordEncoder =
            new AesPasswordEncoder(Base64.getEncoder().encodeToString(new byte[32]));

    private AuthenticationService service;

    @BeforeEach
    void setUp() {
        service = new AuthenticationService(
                userRepository, userSessionRepository, permissionRepository, passwordEncoder, jwtUtil,
                securityEventRecorder);
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
