package com.authen.service;

import com.authen.constant.SecurityEventType;
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
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

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
 * Covers the password check in {@link AuthenticationService#login}: encrypted and legacy
 * plain-text stored passwords, with the real {@link AesPasswordEncoder}.
 */
@ExtendWith(MockitoExtension.class)
class AuthenticationServiceLoginTest {

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
    private HttpServletRequest httpRequest;
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
        ReflectionTestUtils.setField(service, "jwtExpiration", 3_600_000L);
    }

    @Test
    void should_returnToken_when_passwordMatchesEncryptedStoredValue() {
        //GIVEN
        String stored = passwordEncoder.encode(PASSWORD);
        User user = user(stored);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));
        when(jwtUtil.generateToken("admin", 1, "USER_VIP_0")).thenReturn("jwt-token");

        //WHEN
        LoginResponse response = service.login(new LoginRequest("admin", PASSWORD), httpRequest);

        //THEN
        assertThat(response.getToken()).isEqualTo("jwt-token");
        assertThat(user.getPasswordHash()).isEqualTo(stored);
        verify(userSessionRepository).save(any(UserSession.class));
        verifyNoInteractions(securityEventRecorder);
    }

    @Test
    void should_encryptStoredPassword_when_loginWithLegacyPlainTextPassword() {
        //GIVEN
        User user = user(PASSWORD);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));
        when(jwtUtil.generateToken("admin", 1, "USER_VIP_0")).thenReturn("jwt-token");

        //WHEN
        LoginResponse response = service.login(new LoginRequest("admin", PASSWORD), httpRequest);

        //THEN
        assertThat(response.getToken()).isEqualTo("jwt-token");
        assertThat(user.getPasswordHash()).startsWith(AesPasswordEncoder.PREFIX);
        assertThat(passwordEncoder.decrypt(user.getPasswordHash())).isEqualTo(PASSWORD);
        verify(userRepository).save(user);
    }

    @Test
    void should_rejectLogin_when_passwordDoesNotMatchEncryptedStoredValue() {
        //GIVEN
        String stored = passwordEncoder.encode(PASSWORD);
        User user = user(stored);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));

        //WHEN / THEN
        assertThatThrownBy(() -> service.login(new LoginRequest("admin", "wrong"), httpRequest))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Invalid username or password");
        assertThat(user.getPasswordHash()).isEqualTo(stored);
        verify(userSessionRepository, never()).save(any());
        verify(userRepository, never()).save(any());
        verify(securityEventRecorder).record(SecurityEventType.LOGIN_FAILED, httpRequest, "admin", "Wrong password");
    }

    @Test
    void should_rejectLoginAndRecordEvent_when_usernameDoesNotExist() {
        //GIVEN
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        //WHEN / THEN
        assertThatThrownBy(() -> service.login(new LoginRequest("ghost", PASSWORD), httpRequest))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Invalid username or password");
        verify(securityEventRecorder).record(SecurityEventType.LOGIN_FAILED, httpRequest, "ghost", "Unknown username");
        verify(userSessionRepository, never()).save(any());
    }

    @Test
    void should_rejectLoginAndRecordEvent_when_accountIsDisabled() {
        //GIVEN
        User user = user(passwordEncoder.encode(PASSWORD));
        user.setIsActive(false);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));

        //WHEN / THEN
        assertThatThrownBy(() -> service.login(new LoginRequest("admin", PASSWORD), httpRequest))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("User account is disabled");
        verify(securityEventRecorder).record(SecurityEventType.LOGIN_FAILED, httpRequest, "admin", "Account is disabled");
        verify(userSessionRepository, never()).save(any());
    }

    @Test
    void should_rejectLogin_when_passwordIsTheStoredCiphertext() {
        //GIVEN
        String stored = passwordEncoder.encode(PASSWORD);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user(stored)));

        //WHEN / THEN
        assertThatThrownBy(() -> service.login(new LoginRequest("admin", stored), httpRequest))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Invalid username or password");
        verify(userSessionRepository, never()).save(any());
    }

    @Test
    void should_rejectLoginAndKeepPlainText_when_legacyPasswordDoesNotMatch() {
        //GIVEN
        User user = user(PASSWORD);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));

        //WHEN / THEN
        assertThatThrownBy(() -> service.login(new LoginRequest("admin", "wrong"), httpRequest))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Invalid username or password");
        assertThat(user.getPasswordHash()).isEqualTo(PASSWORD);
        verify(userRepository, never()).save(any());
    }

    private User user(String storedPassword) {
        Role role = new Role();
        role.setRoleName("USER_VIP_0");

        User user = new User();
        user.setUserId(1);
        user.setUsername("admin");
        user.setPasswordHash(storedPassword);
        user.setRole(role);
        user.setIsActive(true);
        return user;
    }
}
