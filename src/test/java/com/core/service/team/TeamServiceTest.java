package com.core.service.team;

import com.authen.entity.Role;
import com.authen.entity.User;
import com.authen.repository.UserRepository;
import com.common.exception.PermissionDeniedException;
import com.core.constant.TeamMemberStatus;
import com.core.constant.UserSettingKey;
import com.core.dto.ContactInfo;
import com.core.dto.OpenJobSummary;
import com.core.dto.TeamMemberResponse;
import com.core.dto.TeamMembershipResponse;
import com.core.dto.TeamSettingsDto;
import com.core.entity.TeamMember;
import com.core.entity.UserSetting;
import com.core.repository.DeliveryJobRepository;
import com.core.repository.JobDriverRequestRepository;
import com.core.repository.TeamMemberRepository;
import com.core.repository.UserSettingRepository;
import com.core.service.notification.TeamNotifier;
import com.core.service.profile.ProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers {@link TeamService} (design §5.1, §5.3, §5.4).
 * <ul>
 *   <li>WHAT — invite creates one INVITED row; accept / decline / remove / leave move a row along
 *   INVITED → ACTIVE / DECLINED / CANCELLED and ACTIVE → REMOVED / LEFT; settings store driver.show-price.</li>
 *   <li>WHEN — only an active DRIVER can be invited, only by an owner, never twice while open; only the
 *   addressed driver answers, only from INVITED; only the owner removes; no remove / leave while that pair has
 *   open assigned jobs.</li>
 *   <li>THEN — the row's status and timestamps, the message of the 400, and that nothing is written on a
 *   refusal. The one-open-row rule itself lives in the database: TeamMemberRepositoryTest.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class TeamServiceTest {

    private static final ZoneId BANGKOK = ZoneId.of("Asia/Bangkok");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 1, 10, 0);
    private static final Clock CLOCK = Clock.fixed(NOW.atZone(BANGKOK).toInstant(), BANGKOK);

    private static final User OWNER = user(1, "admin", "USER_VIP_0", true);
    private static final User DRIVER1 = user(3, "driver1", "DRIVER", true);
    private static final User DRIVER2 = user(4, "driver2", "DRIVER", true);

    @Mock
    private TeamMemberRepository teamMemberRepository;
    @Mock
    private UserSettingRepository userSettingRepository;
    @Mock
    private DeliveryJobRepository deliveryJobRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private TeamNotifier teamNotifier;
    @Mock
    private ProfileService profileService;
    @Mock
    private JobDriverRequestRepository jobDriverRequestRepository;

    private TeamService teamService;

    @BeforeEach
    void setUp() {
        teamService = new TeamService(teamMemberRepository, userSettingRepository, deliveryJobRepository,
                userRepository, teamNotifier, profileService, jobDriverRequestRepository, CLOCK);
    }

    private static User user(int id, String username, String roleName, boolean active) {
        Role role = new Role();
        role.setRoleName(roleName);
        User user = new User();
        user.setUserId(id);
        user.setUsername(username);
        user.setRole(role);
        user.setIsActive(active);
        return user;
    }

    private static TeamMember row(long id, User owner, User member, String status) {
        return TeamMember.builder().teamMemberId(id).ownerUserId(owner.getUserId()).memberUserId(member.getUserId())
                .memberRole("DRIVER").memberStatus(status).invitedAt(NOW.minusDays(1)).createdAt(NOW.minusDays(1))
                .build();
    }

    private void loggedIn(User user) {
        when(userRepository.findByUsername(user.getUsername())).thenReturn(Optional.of(user));
    }

    // ---- invite ----

    @Test
    void should_createInvitation_when_inviteeIsActiveDriver() {
        //GIVEN
        loggedIn(OWNER);
        loggedIn(DRIVER1);
        when(teamMemberRepository.insertInvitation(1, 3, NOW, "admin")).thenReturn(1);
        when(teamMemberRepository.findFirstByOwnerUserIdAndMemberUserIdAndMemberStatus(1, 3, TeamMemberStatus.INVITED))
                .thenReturn(Optional.of(row(12L, OWNER, DRIVER1, TeamMemberStatus.INVITED)));
        //WHEN
        TeamMemberResponse result = teamService.invite("admin", "driver1");
        //THEN
        assertThat(result.getTeamMemberId()).isEqualTo(12L);
        assertThat(result.getMemberStatus()).isEqualTo(TeamMemberStatus.INVITED);
        assertThat(result.getMember().getUsername()).isEqualTo("driver1");
        assertThat(result.getMember().getDisplayName()).isEqualTo("driver1");
        verify(teamNotifier).invited(any(TeamMember.class), eq(OWNER), eq(DRIVER1));
    }

    @Test
    void should_findInviteeIgnoringCaseAndSpaces_when_typedDifferently() {
        //GIVEN
        loggedIn(OWNER);
        when(userRepository.findByUsername("Driver1")).thenReturn(Optional.empty());
        when(userRepository.findByUsernameIgnoreCase("Driver1")).thenReturn(List.of(DRIVER1));
        when(teamMemberRepository.insertInvitation(1, 3, NOW, "admin")).thenReturn(1);
        when(teamMemberRepository.findFirstByOwnerUserIdAndMemberUserIdAndMemberStatus(1, 3, TeamMemberStatus.INVITED))
                .thenReturn(Optional.of(row(12L, OWNER, DRIVER1, TeamMemberStatus.INVITED)));
        //WHEN
        TeamMemberResponse result = teamService.invite("admin", "  Driver1 ");
        //THEN
        assertThat(result.getMember().getUserId()).isEqualTo(3);
    }

    @Test
    void should_refuseWithoutWriting_when_usernameIsUnknown() {
        //GIVEN
        loggedIn(OWNER);
        when(userRepository.findByUsername("nobody")).thenReturn(Optional.empty());
        when(userRepository.findByUsernameIgnoreCase("nobody")).thenReturn(List.of());
        //WHEN / THEN
        assertThatThrownBy(() -> teamService.invite("admin", "nobody"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage(TeamService.CANNOT_INVITE);
        verify(teamMemberRepository, never()).insertInvitation(anyInt(), anyInt(), any(), anyString());
    }

    @Test
    void should_refuseWithSameMessage_when_inviteeIsNotADriver() {
        //GIVEN
        loggedIn(OWNER);
        loggedIn(user(2, "owner2", "USER_VIP_0", true));
        //WHEN / THEN
        assertThatThrownBy(() -> teamService.invite("admin", "owner2"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage(TeamService.CANNOT_INVITE);
        verify(teamMemberRepository, never()).insertInvitation(anyInt(), anyInt(), any(), anyString());
    }

    @Test
    void should_refuseWithSameMessage_when_inviteeAccountIsDisabled() {
        //GIVEN
        loggedIn(OWNER);
        loggedIn(user(5, "driver9", "DRIVER", false));
        //WHEN / THEN
        assertThatThrownBy(() -> teamService.invite("admin", "driver9"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage(TeamService.CANNOT_INVITE);
    }

    @Test
    void should_refuseWithSameMessage_when_usernameIsBlank() {
        //GIVEN
        loggedIn(OWNER);
        //WHEN / THEN
        assertThatThrownBy(() -> teamService.invite("admin", "   "))
                .isInstanceOf(IllegalArgumentException.class).hasMessage(TeamService.CANNOT_INVITE);
    }

    @Test
    void should_refuse_when_twoAccountsMatchIgnoringCase() {
        //GIVEN
        loggedIn(OWNER);
        when(userRepository.findByUsername("DRIVER")).thenReturn(Optional.empty());
        when(userRepository.findByUsernameIgnoreCase("DRIVER"))
                .thenReturn(List.of(user(6, "Driver", "DRIVER", true), user(7, "driver", "DRIVER", true)));
        //WHEN / THEN
        assertThatThrownBy(() -> teamService.invite("admin", "DRIVER"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage(TeamService.CANNOT_INVITE);
    }

    @Test
    void should_answerAlreadyInTeam_when_pairHasOpenRow() {
        //GIVEN
        loggedIn(OWNER);
        loggedIn(DRIVER1);
        when(teamMemberRepository.insertInvitation(1, 3, NOW, "admin")).thenReturn(0);
        //WHEN / THEN
        assertThatThrownBy(() -> teamService.invite("admin", "driver1"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage(TeamService.ALREADY_IN_TEAM);
        verifyNoInteractions(teamNotifier);
    }

    @Test
    void should_denyPermission_when_inviterIsADriver() {
        //GIVEN
        loggedIn(DRIVER1);
        //WHEN / THEN
        assertThatThrownBy(() -> teamService.invite("driver1", "driver2"))
                .isInstanceOf(PermissionDeniedException.class);
        verify(teamMemberRepository, never()).insertInvitation(anyInt(), anyInt(), any(), anyString());
    }

    // ---- accept / decline ----

    @Test
    void should_activateAndStampResponse_when_invitedDriverAccepts() {
        //GIVEN
        loggedIn(DRIVER1);
        TeamMember invited = row(12L, OWNER, DRIVER1, TeamMemberStatus.INVITED);
        when(teamMemberRepository.findForUpdate(12L)).thenReturn(Optional.of(invited));
        when(userRepository.findById(1)).thenReturn(Optional.of(OWNER));
        //WHEN
        TeamMembershipResponse result = teamService.acceptInvitation("driver1", 12L);
        //THEN
        assertThat(invited.getMemberStatus()).isEqualTo(TeamMemberStatus.ACTIVE);
        assertThat(invited.getRespondedAt()).isEqualTo(NOW);
        assertThat(invited.getEndedAt()).isNull();
        assertThat(invited.getUpdatedBy()).isEqualTo("driver1");
        assertThat(result.getOwner().getUsername()).isEqualTo("admin");
        verify(teamNotifier).answered(invited, OWNER, DRIVER1, true);
    }

    @Test
    void should_declineAndEnd_when_invitedDriverDeclines() {
        //GIVEN
        loggedIn(DRIVER1);
        TeamMember invited = row(12L, OWNER, DRIVER1, TeamMemberStatus.INVITED);
        when(teamMemberRepository.findForUpdate(12L)).thenReturn(Optional.of(invited));
        //WHEN
        teamService.declineInvitation("driver1", 12L);
        //THEN
        verify(teamNotifier).answered(invited, null, DRIVER1, false);
        assertThat(invited.getMemberStatus()).isEqualTo(TeamMemberStatus.DECLINED);
        assertThat(invited.getRespondedAt()).isEqualTo(NOW);
        assertThat(invited.getEndedAt()).isEqualTo(NOW);
    }

    @Test
    void should_answerNotFound_when_invitationIsForAnotherDriver() {
        //GIVEN
        loggedIn(DRIVER2);
        TeamMember invited = row(12L, OWNER, DRIVER1, TeamMemberStatus.INVITED);
        when(teamMemberRepository.findForUpdate(12L)).thenReturn(Optional.of(invited));
        //WHEN / THEN
        assertThatThrownBy(() -> teamService.acceptInvitation("driver2", 12L))
                .isInstanceOf(IllegalArgumentException.class).hasMessage(TeamService.INVITATION_NOT_FOUND);
        assertThat(invited.getMemberStatus()).isEqualTo(TeamMemberStatus.INVITED);
    }

    @Test
    void should_refuseAnswer_when_ownerCancelledMeanwhile() {
        //GIVEN
        loggedIn(DRIVER1);
        TeamMember cancelled = row(12L, OWNER, DRIVER1, TeamMemberStatus.CANCELLED);
        when(teamMemberRepository.findForUpdate(12L)).thenReturn(Optional.of(cancelled));
        //WHEN / THEN
        assertThatThrownBy(() -> teamService.acceptInvitation("driver1", 12L))
                .isInstanceOf(IllegalArgumentException.class).hasMessage(TeamService.INVITATION_CLOSED);
        assertThat(cancelled.getMemberStatus()).isEqualTo(TeamMemberStatus.CANCELLED);
    }

    // ---- remove (owner) ----

    @Test
    void should_cancelInvitation_when_ownerRemovesInvitedRow() {
        //GIVEN
        loggedIn(OWNER);
        TeamMember invited = row(12L, OWNER, DRIVER2, TeamMemberStatus.INVITED);
        when(teamMemberRepository.findForUpdate(12L)).thenReturn(Optional.of(invited));
        //WHEN
        teamService.removeMember("admin", 12L);
        //THEN
        assertThat(invited.getMemberStatus()).isEqualTo(TeamMemberStatus.CANCELLED);
        assertThat(invited.getEndedAt()).isEqualTo(NOW);
        verifyNoInteractions(teamNotifier);
        verify(deliveryJobRepository, never()).countOpenAssigned(anyString(), anyInt());
    }

    @Test
    void should_removeMember_when_activeWithoutOpenJobs() {
        //GIVEN
        loggedIn(OWNER);
        TeamMember active = row(12L, OWNER, DRIVER1, TeamMemberStatus.ACTIVE);
        when(teamMemberRepository.findForUpdate(12L)).thenReturn(Optional.of(active));
        when(deliveryJobRepository.countOpenAssigned("admin", 3)).thenReturn(0L);
        when(userRepository.findById(3)).thenReturn(Optional.of(DRIVER1));
        //WHEN
        teamService.removeMember("admin", 12L);
        //THEN
        assertThat(active.getMemberStatus()).isEqualTo(TeamMemberStatus.REMOVED);
        assertThat(active.getEndedAt()).isEqualTo(NOW);
        assertThat(active.getUpdatedBy()).isEqualTo("admin");
        verify(teamNotifier).removed(active, OWNER, DRIVER1);
        // a waiting job request does not block the removal but goes with it (assignConfirm G5)
        verify(jobDriverRequestRepository).cancelPendingOfPair(1, 3, NOW, "admin");
    }

    @Test
    void should_blockRemoval_when_memberHasOpenJobs() {
        //GIVEN
        loggedIn(OWNER);
        TeamMember active = row(12L, OWNER, DRIVER1, TeamMemberStatus.ACTIVE);
        when(teamMemberRepository.findForUpdate(12L)).thenReturn(Optional.of(active));
        when(deliveryJobRepository.countOpenAssigned("admin", 3)).thenReturn(2L);
        //WHEN / THEN
        assertThatThrownBy(() -> teamService.removeMember("admin", 12L))
                .isInstanceOf(IllegalArgumentException.class).hasMessage(TeamService.MEMBER_HAS_OPEN_JOBS);
        assertThat(active.getMemberStatus()).isEqualTo(TeamMemberStatus.ACTIVE);
        verifyNoInteractions(teamNotifier);
    }

    @Test
    void should_answerNotFound_when_rowBelongsToAnotherOwner() {
        //GIVEN
        loggedIn(OWNER);
        User owner2 = user(2, "owner2", "USER_VIP_0", true);
        when(teamMemberRepository.findForUpdate(12L))
                .thenReturn(Optional.of(row(12L, owner2, DRIVER1, TeamMemberStatus.ACTIVE)));
        //WHEN / THEN
        assertThatThrownBy(() -> teamService.removeMember("admin", 12L))
                .isInstanceOf(IllegalArgumentException.class).hasMessage(TeamService.MEMBER_NOT_FOUND);
    }

    @Test
    void should_answerAlreadyGone_when_rowIsFinal() {
        //GIVEN
        loggedIn(OWNER);
        when(teamMemberRepository.findForUpdate(12L))
                .thenReturn(Optional.of(row(12L, OWNER, DRIVER1, TeamMemberStatus.LEFT)));
        //WHEN / THEN
        assertThatThrownBy(() -> teamService.removeMember("admin", 12L))
                .isInstanceOf(IllegalArgumentException.class).hasMessage(TeamService.MEMBER_ALREADY_GONE);
    }

    // ---- leave (driver) ----

    @Test
    void should_leaveTeam_when_activeWithoutOpenJobs() {
        //GIVEN
        loggedIn(DRIVER1);
        TeamMember active = row(12L, OWNER, DRIVER1, TeamMemberStatus.ACTIVE);
        when(teamMemberRepository.findForUpdate(12L)).thenReturn(Optional.of(active));
        when(userRepository.findById(1)).thenReturn(Optional.of(OWNER));
        when(deliveryJobRepository.countOpenAssigned("admin", 3)).thenReturn(0L);
        //WHEN
        teamService.leaveTeam("driver1", 12L);
        //THEN
        assertThat(active.getMemberStatus()).isEqualTo(TeamMemberStatus.LEFT);
        assertThat(active.getEndedAt()).isEqualTo(NOW);
        assertThat(active.getUpdatedBy()).isEqualTo("driver1");
        verify(teamNotifier).left(active, OWNER, DRIVER1);
        verify(jobDriverRequestRepository).cancelPendingOfPair(1, 3, NOW, "driver1");
    }

    @Test
    void should_blockLeaving_when_openJobsInThatTeam() {
        //GIVEN
        loggedIn(DRIVER1);
        TeamMember active = row(12L, OWNER, DRIVER1, TeamMemberStatus.ACTIVE);
        when(teamMemberRepository.findForUpdate(12L)).thenReturn(Optional.of(active));
        when(userRepository.findById(1)).thenReturn(Optional.of(OWNER));
        when(deliveryJobRepository.countOpenAssigned("admin", 3)).thenReturn(1L);
        //WHEN / THEN
        assertThatThrownBy(() -> teamService.leaveTeam("driver1", 12L))
                .isInstanceOf(IllegalArgumentException.class).hasMessage(TeamService.TEAM_HAS_OPEN_JOBS);
        assertThat(active.getMemberStatus()).isEqualTo(TeamMemberStatus.ACTIVE);
    }

    @Test
    void should_answerTeamNotFound_when_rowIsStillInvited() {
        //GIVEN
        loggedIn(DRIVER1);
        when(teamMemberRepository.findForUpdate(12L))
                .thenReturn(Optional.of(row(12L, OWNER, DRIVER1, TeamMemberStatus.INVITED)));
        //WHEN / THEN
        assertThatThrownBy(() -> teamService.leaveTeam("driver1", 12L))
                .isInstanceOf(IllegalArgumentException.class).hasMessage(TeamService.TEAM_NOT_FOUND);
    }

    // ---- lists ----

    private static DeliveryJobRepository.OpenAssignedJob openJob(int driver, long jobId, String deliveryStatus,
                                                                 LocalDateTime scheduledDate) {
        return new DeliveryJobRepository.OpenAssignedJob() {
            public Integer getDriverUserId() { return driver; }
            public Long getJobId() { return jobId; }
            public String getJobCode() { return "JOB-" + jobId; }
            public String getJobName() { return null; }
            public String getDeliveryStatus() { return deliveryStatus; }
            public LocalDateTime getScheduledDate() { return scheduledDate; }
            public long getStopCount() { return 5L; }
            public long getCompletedStopCount() { return 2L; }
        };
    }

    @Test
    void should_attachOpenJobsAndCountTheList_when_listingMembers() {
        //GIVEN — driver1 (ACTIVE) has 2 open jobs · driver2 (INVITED) has none
        loggedIn(OWNER);
        when(teamMemberRepository.findByOwnerUserIdAndMemberStatusInOrderByCreatedAtAsc(1, TeamMemberStatus.OPEN))
                .thenReturn(List.of(row(12L, OWNER, DRIVER1, TeamMemberStatus.ACTIVE),
                        row(13L, OWNER, DRIVER2, TeamMemberStatus.INVITED)));
        when(userRepository.findAllById(List.of(3, 4))).thenReturn(List.of(DRIVER1, DRIVER2));
        when(deliveryJobRepository.findOpenAssignedByOwner("admin")).thenReturn(List.of(
                openJob(3, 41L, "NEW", NOW.plusDays(1)), openJob(3, 42L, "NEW", NOW.plusDays(2))));
        //WHEN
        List<TeamMemberResponse> result = teamService.listMembers("admin");
        //THEN
        assertThat(result).extracting(TeamMemberResponse::getTeamMemberId).containsExactly(12L, 13L);
        assertThat(result).extracting(TeamMemberResponse::getOpenJobCount).containsExactly(2L, 0L);
        assertThat(result.get(0).getOpenJobs()).extracting(OpenJobSummary::getJobId, OpenJobSummary::getJobCode,
                        OpenJobSummary::getStopCount, OpenJobSummary::getCompletedStopCount)
                .containsExactly(tuple(41L, "JOB-41", 5L, 2L), tuple(42L, "JOB-42", 5L, 2L));
        assertThat(result.get(1).getOpenJobs()).isEmpty();
    }

    @Test
    void should_putStartedJobsFirstThenNearestDateThenNoDate_when_listingOpenJobs() {
        //GIVEN — arrives in jobId order; the started one is scheduled latest, one NEW job has no date
        loggedIn(OWNER);
        when(teamMemberRepository.findByOwnerUserIdAndMemberStatusInOrderByCreatedAtAsc(1, TeamMemberStatus.OPEN))
                .thenReturn(List.of(row(12L, OWNER, DRIVER1, TeamMemberStatus.ACTIVE)));
        when(userRepository.findAllById(List.of(3))).thenReturn(List.of(DRIVER1));
        when(deliveryJobRepository.findOpenAssignedByOwner("admin")).thenReturn(List.of(
                openJob(3, 41L, "NEW", null),
                openJob(3, 42L, "NEW", NOW.plusDays(3)),
                openJob(3, 43L, "NEW", NOW.plusDays(1)),
                openJob(3, 44L, "IN_PROGRESS", NOW.plusDays(5))));
        //WHEN
        List<TeamMemberResponse> result = teamService.listMembers("admin");
        //THEN
        assertThat(result.get(0).getOpenJobs()).extracting(OpenJobSummary::getJobId)
                .containsExactly(44L, 43L, 42L, 41L);
    }

    @Test
    void should_returnEmpty_when_driverHasNoTeams() {
        //GIVEN
        loggedIn(DRIVER2);
        when(teamMemberRepository.findByMemberUserIdAndMemberStatusOrderByCreatedAtDesc(4, TeamMemberStatus.ACTIVE))
                .thenReturn(List.of());
        //WHEN
        List<TeamMembershipResponse> result = teamService.listMemberships("driver2");
        //THEN
        assertThat(result).isEmpty();
    }

    // ---- settings ----

    @Test
    void should_hidePrices_when_ownerNeverSetIt() {
        //GIVEN
        loggedIn(OWNER);
        when(userSettingRepository.findById(new UserSetting.Key(1, UserSettingKey.DRIVER_SHOW_PRICE)))
                .thenReturn(Optional.empty());
        //WHEN
        TeamSettingsDto result = teamService.getSettings("admin");
        //THEN
        assertThat(result.getShowPriceToDriver()).isFalse();
    }

    @Test
    void should_storeTrue_when_ownerTurnsPricesOn() {
        //GIVEN
        loggedIn(OWNER);
        ArgumentCaptor<UserSetting> saved = ArgumentCaptor.forClass(UserSetting.class);
        //WHEN
        TeamSettingsDto result = teamService.updateSettings("admin", new TeamSettingsDto(true));
        //THEN
        verify(userSettingRepository).save(saved.capture());
        assertThat(saved.getValue().getId()).isEqualTo(new UserSetting.Key(1, UserSettingKey.DRIVER_SHOW_PRICE));
        assertThat(saved.getValue().getSettingValue()).isEqualTo("true");
        assertThat(saved.getValue().getUpdatedAt()).isEqualTo(NOW);
        assertThat(result.getShowPriceToDriver()).isTrue();
    }

    @Test
    void should_denyPermission_when_driverChangesSettings() {
        //GIVEN
        loggedIn(DRIVER1);
        //WHEN / THEN
        assertThatThrownBy(() -> teamService.updateSettings("driver1", new TeamSettingsDto(true)))
                .isInstanceOf(PermissionDeniedException.class);
        verify(userSettingRepository, never()).save(any());
    }

    // ---- contact / availability (profile.md US-PROF-04 / 05) ----

    @Test
    void should_attachContactAvailabilityDeliveringAndLastSeen_when_listingOpenMembers() {
        //GIVEN — driver 5 is ACTIVE and delivering, driver 6 is INVITED and BUSY
        User active = user(5, "driver5", "DRIVER", true);
        active.setLastSeenAt(NOW.minusMinutes(3));
        User invited = user(6, "driver6", "DRIVER", true);
        loggedIn(OWNER);
        when(teamMemberRepository.findByOwnerUserIdAndMemberStatusInOrderByCreatedAtAsc(1, TeamMemberStatus.OPEN))
                .thenReturn(List.of(row(20L, OWNER, active, TeamMemberStatus.ACTIVE),
                        row(21L, OWNER, invited, TeamMemberStatus.INVITED)));
        when(userRepository.findAllById(List.of(5, 6))).thenReturn(List.of(active, invited));
        when(profileService.contactsOf(any())).thenReturn(Map.of(
                5, new ContactInfo("0891000005", "ต.ธาตุเชิงชุม อ.เมืองสกลนคร จ.สกลนคร"),
                6, new ContactInfo(null, null)));
        when(profileService.availabilityOf(any())).thenReturn(Map.of(5, "AVAILABLE", 6, "BUSY"));
        when(profileService.deliveringOf(any())).thenReturn(Set.of(5));
        //WHEN
        List<TeamMemberResponse> result = teamService.listMembers("admin");
        //THEN
        assertThat(result.get(0).getContact().getPhone()).isEqualTo("0891000005");
        assertThat(result.get(0).getAvailability()).isEqualTo("AVAILABLE");
        assertThat(result.get(0).isDelivering()).isTrue();
        assertThat(result.get(0).getLastSeenAt()).isEqualTo(NOW.minusMinutes(3));
        assertThat(result.get(1).getAvailability()).isEqualTo("BUSY");
        assertThat(result.get(1).isDelivering()).isFalse();
        assertThat(result.get(1).getLastSeenAt()).isNull();
    }

    @Test
    void should_attachOwnerContact_when_driverListsInvitations() {
        //GIVEN
        loggedIn(DRIVER2);
        when(teamMemberRepository.findByMemberUserIdAndMemberStatusOrderByCreatedAtDesc(4, TeamMemberStatus.INVITED))
                .thenReturn(List.of(row(30L, OWNER, DRIVER2, TeamMemberStatus.INVITED)));
        when(userRepository.findAllById(List.of(1))).thenReturn(List.of(OWNER));
        when(profileService.contactsOf(any())).thenReturn(Map.of(1, new ContactInfo("0812000001", "45 ถ.สุขเกษม")));
        //WHEN
        List<TeamMembershipResponse> result = teamService.listInvitations("driver2");
        //THEN
        assertThat(result).extracting(r -> r.getContact().getPhone()).containsExactly("0812000001");
    }

    @Test
    void should_returnNoContact_when_rowEndedBeforeResponse() {
        //GIVEN — the owner withdraws an invitation: the answer shows the ended row without the driver's data
        loggedIn(OWNER);
        TeamMember invitation = row(40L, OWNER, DRIVER2, TeamMemberStatus.INVITED);
        when(teamMemberRepository.findForUpdate(40L)).thenReturn(Optional.of(invitation));
        when(userRepository.findById(4)).thenReturn(Optional.of(DRIVER2));
        //WHEN
        TeamMemberResponse result = teamService.removeMember("admin", 40L);
        //THEN
        assertThat(result.getMemberStatus()).isEqualTo(TeamMemberStatus.CANCELLED);
        assertThat(result.getContact()).isNull();
        verifyNoInteractions(profileService);
    }
}
