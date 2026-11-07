package com.core.service.team;

import com.authen.entity.User;
import com.authen.repository.UserRepository;
import com.core.constant.TeamMemberStatus;
import com.core.constant.UserSettingKey;
import com.core.entity.DeliveryJob;
import com.core.entity.TeamMember;
import com.core.entity.UserSetting;
import com.core.repository.DeliveryJobRepository;
import com.core.repository.TeamMemberRepository;
import com.core.repository.UserSettingRepository;
import com.core.service.notification.TeamNotifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers {@link JobAccessService} — the driver half of canOperateJob (design §4.2) and the price setting per job.
 * <ul>
 *   <li>WHAT — isAssignedActiveDriver answers whether this user may operate a job they do not own; describe gives
 *   owner, driver and driverSeesPrice per job; lockActiveMember accepts only an ACTIVE member.</li>
 *   <li>WHEN — assigned to me AND ACTIVE in the job owner's team; price only when the owner stored "true".</li>
 *   <li>THEN — the boolean / values returned, and that membership is not even queried when the job is not mine.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class JobAccessServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private TeamMemberRepository teamMemberRepository;
    @Mock
    private UserSettingRepository userSettingRepository;
    @Mock
    private DeliveryJobRepository deliveryJobRepository;
    @Mock
    private TeamNotifier teamNotifier;

    private JobAccessService jobAccessService;

    @BeforeEach
    void setUp() {
        jobAccessService = new JobAccessService(userRepository, teamMemberRepository, userSettingRepository,
                deliveryJobRepository, teamNotifier);
    }

    private static User user(int id, String username, String firstName) {
        User user = new User();
        user.setUserId(id);
        user.setUsername(username);
        user.setFirstName(firstName);
        return user;
    }

    private static DeliveryJob job(long id, String owner, Integer driverId) {
        return DeliveryJob.builder().jobId(id).createdBy(owner).assignedDriverId(driverId).status("A")
                .deliveryStatus("NEW").build();
    }

    @Test
    void should_allow_when_jobIsAssignedToMeAndIAmActiveMember() {
        //GIVEN
        when(userRepository.findByUsername("driver1")).thenReturn(Optional.of(user(3, "driver1", null)));
        when(teamMemberRepository.existsActiveMembership("admin", 3)).thenReturn(true);
        //WHEN
        boolean result = jobAccessService.isAssignedActiveDriver(job(1L, "admin", 3), "driver1");
        //THEN
        assertThat(result).isTrue();
    }

    @Test
    void should_deny_when_assignedToMeButNoLongerActiveMember() {
        //GIVEN
        when(userRepository.findByUsername("driver1")).thenReturn(Optional.of(user(3, "driver1", null)));
        when(teamMemberRepository.existsActiveMembership("admin", 3)).thenReturn(false);
        //WHEN
        boolean result = jobAccessService.isAssignedActiveDriver(job(1L, "admin", 3), "driver1");
        //THEN
        assertThat(result).isFalse();
    }

    @Test
    void should_denyWithoutCheckingTeam_when_jobIsAssignedToSomeoneElse() {
        //GIVEN
        when(userRepository.findByUsername("driver2")).thenReturn(Optional.of(user(4, "driver2", null)));
        //WHEN
        boolean result = jobAccessService.isAssignedActiveDriver(job(1L, "admin", 3), "driver2");
        //THEN
        assertThat(result).isFalse();
        verify(teamMemberRepository, never()).existsActiveMembership(anyString(), anyInt());
    }

    @Test
    void should_denyWithoutAnyLookup_when_jobHasNoDriver() {
        //GIVEN
        DeliveryJob unassigned = job(1L, "admin", null);
        //WHEN
        boolean result = jobAccessService.isAssignedActiveDriver(unassigned, "driver1");
        //THEN
        assertThat(result).isFalse();
        verify(userRepository, never()).findByUsername(anyString());
    }

    @Test
    void should_refuse_when_lockingDriverWhoIsNotActiveMember() {
        //GIVEN
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user(1, "admin", null)));
        when(teamMemberRepository.findActiveForUpdate(1, 9)).thenReturn(Optional.empty());
        //WHEN / THEN
        assertThatThrownBy(() -> jobAccessService.lockActiveMember("admin", 9))
                .isInstanceOf(IllegalArgumentException.class).hasMessage(JobAccessService.NOT_A_MEMBER);
    }

    @Test
    void should_returnDriver_when_lockingActiveMember() {
        //GIVEN
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user(1, "admin", null)));
        when(teamMemberRepository.findActiveForUpdate(1, 3)).thenReturn(Optional.of(TeamMember.builder()
                .ownerUserId(1).memberUserId(3).memberStatus(TeamMemberStatus.ACTIVE).createdAt(LocalDateTime.MIN)
                .build()));
        when(userRepository.findById(3)).thenReturn(Optional.of(user(3, "driver1", null)));
        //WHEN
        User driver = jobAccessService.lockActiveMember("admin", 3);
        //THEN
        assertThat(driver.getUsername()).isEqualTo("driver1");
    }

    @Test
    void should_markPriceVisiblePerOwner_when_describingJobsOfTwoTeams() {
        //GIVEN — admin allows prices, owner2 never set it
        DeliveryJob adminJob = job(1L, "admin", 3);
        DeliveryJob owner2Job = job(2L, "owner2", null);
        when(userRepository.findByUsernameIn(any())).thenReturn(List.of(user(1, "admin", "สมชาย"), user(2, "owner2", null)));
        when(userRepository.findAllById(any())).thenReturn(List.of(user(3, "driver1", "ประยุทธ")));
        when(userSettingRepository.findAllById(any())).thenReturn(List.of(UserSetting.builder()
                .id(new UserSetting.Key(1, UserSettingKey.DRIVER_SHOW_PRICE)).settingValue("true").build()));
        //WHEN
        Map<Long, JobAccessService.JobPeople> people = jobAccessService.describe(List.of(adminJob, owner2Job));
        //THEN
        assertThat(people.get(1L).driverSeesPrice()).isTrue();
        assertThat(people.get(1L).owner().getDisplayName()).isEqualTo("สมชาย");
        assertThat(people.get(1L).assignedDriver().getUsername()).isEqualTo("driver1");
        assertThat(people.get(2L).driverSeesPrice()).isFalse();
        assertThat(people.get(2L).owner().getDisplayName()).isEqualTo("owner2");
        assertThat(people.get(2L).assignedDriver()).isNull();
    }

    @Test
    void should_tellPreviousDriver_when_jobIsTakenAwayFromThem() {
        //GIVEN
        DeliveryJob job = job(1L, "admin", null);
        User driver1 = user(3, "driver1", null);
        when(userRepository.findById(3)).thenReturn(Optional.of(driver1));
        //WHEN
        jobAccessService.notifyUnassigned(job, 3);
        //THEN
        verify(teamNotifier).jobUnassigned(job, driver1);
    }

    @Test
    void should_notifyNobody_when_jobHadNoDriver() {
        //GIVEN
        DeliveryJob job = job(1L, "admin", null);
        //WHEN
        jobAccessService.notifyUnassigned(job, null);
        //THEN
        verifyNoInteractions(teamNotifier);
    }

    @Test
    void should_returnEmpty_when_noJobs() {
        //GIVEN
        List<DeliveryJob> none = List.of();
        //WHEN
        Map<Long, JobAccessService.JobPeople> people = jobAccessService.describe(none);
        //THEN
        assertThat(people).isEmpty();
        verify(userRepository, never()).findByUsernameIn(any());
    }

    @Test
    void should_markSameTeamOnlyForOpenPair_when_describingJobsWithDrivers() {
        //GIVEN — admin's driver1 is in admin's team; owner2's job is assigned to driver2, who has no open row
        User admin = user(1, "admin", null);
        admin.setPhone("0812000001");
        User driver1 = user(3, "driver1", null);
        driver1.setPhone("0891000001");
        when(userRepository.findByUsernameIn(any())).thenReturn(List.of(admin, user(2, "owner2", null)));
        when(userRepository.findAllById(any())).thenReturn(List.of(driver1, user(4, "driver2", null)));
        when(teamMemberRepository.findByOwnerUserIdInAndMemberUserIdInAndMemberStatusIn(any(), any(), any()))
                .thenReturn(List.of(TeamMember.builder().ownerUserId(1).memberUserId(3)
                        .memberStatus(TeamMemberStatus.ACTIVE).build()));
        //WHEN
        Map<Long, JobAccessService.JobPeople> people = jobAccessService.describe(
                List.of(job(1L, "admin", 3), job(2L, "owner2", 4)));
        //THEN
        assertThat(people.get(1L).sameTeam()).isTrue();
        assertThat(people.get(1L).ownerPhone()).isEqualTo("0812000001");
        assertThat(people.get(1L).driverPhone()).isEqualTo("0891000001");
        assertThat(people.get(2L).sameTeam()).isFalse();
    }
}
