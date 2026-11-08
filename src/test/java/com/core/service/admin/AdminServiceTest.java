package com.core.service.admin;

import com.authen.entity.Role;
import com.authen.entity.User;
import com.authen.repository.UserRepository;
import com.authen.repository.UserSessionRepository;
import com.common.exception.PermissionDeniedException;
import com.core.repository.DeliveryJobRepository;
import com.core.repository.DeliveryRoutingRepository;
import com.core.repository.JobDriverRequestRepository;
import com.core.repository.OrderRepository;
import com.core.repository.TeamMemberRepository;
import com.core.service.JobService;
import com.core.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Covers {@link AdminService} (admin.md US-ADM-01 – 05).
 * <ul>
 *   <li>WHAT — every call first requires an active, not deleted ADMIN caller (else 403); disable / delete / new
 *   password end the user's sessions; delete is a soft delete that also ends the user's team rows and pending
 *   requests; ADMIN accounts cannot be changed from the menu.</li>
 *   <li>WHEN — delete refused while the user still has open jobs (owned or assigned).</li>
 *   <li>THEN — the user fields written, the repositories called, and nothing written on a refusal.</li>
 * </ul>
 * Order / job rules are in OrderServiceAdminTest / JobServiceAdminTest (here they are only delegated).
 * ZOMBIES: Many / Boundary skipped — one user per call; list limits are plain query parameters.
 */
@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    private static final ZoneId BANGKOK = ZoneId.of("Asia/Bangkok");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 7, 10, 0);
    private static final Clock CLOCK = Clock.fixed(NOW.atZone(BANGKOK).toInstant(), BANGKOK);

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserSessionRepository userSessionRepository;
    @Mock
    private TeamMemberRepository teamMemberRepository;
    @Mock
    private JobDriverRequestRepository jobDriverRequestRepository;
    @Mock
    private DeliveryJobRepository deliveryJobRepository;
    @Mock
    private DeliveryRoutingRepository deliveryRoutingRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderService orderService;
    @Mock
    private JobService jobService;
    @Mock
    private PasswordEncoder passwordEncoder;

    private AdminService adminService;

    @BeforeEach
    void setUp() {
        adminService = new AdminService(userRepository, userSessionRepository, teamMemberRepository,
                jobDriverRequestRepository, deliveryJobRepository, deliveryRoutingRepository, orderRepository,
                orderService, jobService, passwordEncoder, CLOCK);
    }

    private static User user(int id, String username, String role, boolean active) {
        Role r = new Role();
        r.setRoleName(role);
        User u = new User();
        u.setUserId(id);
        u.setUsername(username);
        u.setRole(r);
        u.setIsActive(active);
        return u;
    }

    private User givenAdmin() {
        User admin = user(1, "god", "ADMIN", true);
        when(userRepository.findByUsername("god")).thenReturn(Optional.of(admin));
        return admin;
    }

    private User givenTarget(String role) {
        User target = user(7, "driver1", role, true);
        when(userRepository.findById(7)).thenReturn(Optional.of(target));
        return target;
    }

    // ---- requireAdmin ----

    @Test
    void should_answer403_when_callerIsAnOwner() {
        //GIVEN
        when(userRepository.findByUsername("owner1")).thenReturn(Optional.of(user(2, "owner1", "USER_VIP_0", true)));

        //WHEN / THEN
        assertThatThrownBy(() -> adminService.deleteJob("owner1", 5L))
                .isInstanceOf(PermissionDeniedException.class);
        verifyNoInteractions(jobService);
    }

    @Test
    void should_answer403_when_callerIsADriver() {
        //GIVEN
        when(userRepository.findByUsername("driver1")).thenReturn(Optional.of(user(7, "driver1", "DRIVER", true)));

        //WHEN / THEN
        assertThatThrownBy(() -> adminService.resetOrder("driver1", 5L))
                .isInstanceOf(PermissionDeniedException.class);
        verifyNoInteractions(orderService);
    }

    @Test
    void should_answer403_when_adminAccountIsDisabled() {
        //GIVEN
        when(userRepository.findByUsername("god")).thenReturn(Optional.of(user(1, "god", "ADMIN", false)));

        //WHEN / THEN
        assertThatThrownBy(() -> adminService.listUsers("god", null))
                .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    void should_answer403_when_tokenHasNoUsername() {
        //GIVEN — no username could be read from the token

        //WHEN / THEN
        assertThatThrownBy(() -> adminService.listJobs(null, null))
                .isInstanceOf(PermissionDeniedException.class);
        verifyNoInteractions(deliveryJobRepository);
    }

    @Test
    void should_delegateReset_when_callerIsAdmin() {
        //GIVEN
        givenAdmin();

        //WHEN
        adminService.resetJob("god", 5L);

        //THEN
        verify(jobService).resetJobAsAdmin(5L, "god");
    }

    // ---- disable / enable ----

    @Test
    void should_deactivateAndEndSessions_when_userIsDisabled() {
        //GIVEN
        givenAdmin();
        User target = givenTarget("DRIVER");

        //WHEN
        adminService.disableUser("god", 7);

        //THEN
        assertThat(target.getIsActive()).isFalse();
        verify(userRepository).save(target);
        verify(userSessionRepository).deleteByUserId(7);
    }

    @Test
    void should_refuse_when_targetIsAnAdminAccount() {
        //GIVEN
        givenAdmin();
        User target = givenTarget("ADMIN");

        //WHEN / THEN
        assertThatThrownBy(() -> adminService.disableUser("god", 7))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(AdminService.ADMIN_ACCOUNT);
        assertThat(target.getIsActive()).isTrue();
        verify(userSessionRepository, never()).deleteByUserId(anyInt());
    }

    @Test
    void should_answerNotFound_when_enablingADeletedUser() {
        //GIVEN
        givenAdmin();
        User target = givenTarget("DRIVER");
        target.setIsActive(false);
        target.setDeletedAt(NOW.minusDays(1));

        //WHEN / THEN
        assertThatThrownBy(() -> adminService.enableUser("god", 7))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(AdminService.USER_NOT_FOUND);
        assertThat(target.getIsActive()).isFalse();
    }

    // ---- delete ----

    @Test
    void should_softDeleteAndEndTeamsAndRequests_when_userHasNoOpenJobs() {
        //GIVEN
        givenAdmin();
        User target = givenTarget("DRIVER");
        when(deliveryJobRepository.countOpenOfUser("driver1", 7)).thenReturn(0L);

        //WHEN
        adminService.deleteUser("god", 7);

        //THEN
        assertThat(target.getIsActive()).isFalse();
        assertThat(target.getDeletedAt()).isEqualTo(NOW);
        verify(userSessionRepository).deleteByUserId(7);
        verify(teamMemberRepository).endAllOfUser(7, NOW, "god");
        verify(jobDriverRequestRepository).cancelPendingOfUser(7, NOW, "god");
    }

    @Test
    void should_refuseDelete_when_userHasOpenJobs() {
        //GIVEN
        givenAdmin();
        User target = givenTarget("USER_VIP_0");
        when(deliveryJobRepository.countOpenOfUser("driver1", 7)).thenReturn(2L);

        //WHEN / THEN
        assertThatThrownBy(() -> adminService.deleteUser("god", 7))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2 งาน");
        assertThat(target.getDeletedAt()).isNull();
        verify(userRepository, never()).save(any());
        verifyNoInteractions(teamMemberRepository, jobDriverRequestRepository);
    }

    // ---- password ----

    @Test
    void should_storeEncodedPasswordAndEndSessions_when_adminSetsNewPassword() {
        //GIVEN
        givenAdmin();
        User target = givenTarget("DRIVER");
        when(passwordEncoder.encode("new-pass-1")).thenReturn("{aes}encoded");

        //WHEN
        adminService.changePassword("god", 7, "new-pass-1");

        //THEN
        assertThat(target.getPasswordHash()).isEqualTo("{aes}encoded");
        verify(userRepository).save(target);
        verify(userSessionRepository).deleteByUserId(7);
    }

    // ---- pattern ----

    @Test
    void should_matchEverything_when_queryIsBlank() {
        //GIVEN / WHEN / THEN
        assertThat(AdminService.pattern(null)).isEqualTo("%%");
        assertThat(AdminService.pattern("  ")).isEqualTo("%%");
    }

    @Test
    void should_lowerCaseAndDropWildcards_when_queryIsTyped() {
        //GIVEN / WHEN / THEN
        assertThat(AdminService.pattern(" Driver_1% ")).isEqualTo("%driver1%");
    }
}
