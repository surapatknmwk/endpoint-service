package com.core.service.team;

import com.authen.entity.Role;
import com.authen.entity.User;
import com.authen.repository.UserRepository;
import com.common.exception.PermissionDeniedException;
import com.core.constant.JobDriverRequestStatus;
import com.core.constant.TeamMemberStatus;
import com.core.dto.DeclineRequest;
import com.core.dto.DriverRequestResponse;
import com.core.dto.master.AddressInfoDto;
import com.core.entity.DeliveryJob;
import com.core.entity.DeliveryRouting;
import com.core.entity.JobDriverRequest;
import com.core.entity.Order;
import com.core.entity.OrderAddress;
import com.core.entity.TeamMember;
import com.core.repository.DeliveryJobEventRepository;
import com.core.repository.DeliveryJobRepository;
import com.core.repository.DeliveryRoutingRepository;
import com.core.repository.JobDriverRequestRepository;
import com.core.repository.TeamMemberRepository;
import com.core.service.clients.MasterDataService;
import com.core.service.notification.TeamNotifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Covers {@link JobDriverRequestService} — choosing a driver asks them first (docs/task/enhanment/assignConfirm).
 * <ul>
 *   <li>WHAT — requestDriver creates a PENDING request (and unassigns / cancels what was there); accept gives the job
 *   to the driver; decline records an optional reason; the owner is told either way; describe / listMyRequests show
 *   the right request to the right person.</li>
 *   <li>WHEN — only while the job is NEW, only an ACTIVE member; answering only a PENDING request of mine, of a NEW
 *   job, while I am still ACTIVE; a typed note only with reason OTHER.</li>
 *   <li>THEN — request status / reason, {@code assigned_driver_id}, which notification went to whom, and the 400 / 403
 *   answers. The one-pending-per-job rule itself lives in the database: JobDriverRequestRepositoryTest.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class JobDriverRequestServiceTest {

    private static final ZoneId BANGKOK = ZoneId.of("Asia/Bangkok");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 2, 10, 0);
    private static final Clock CLOCK = Clock.fixed(NOW.atZone(BANGKOK).toInstant(), BANGKOK);

    private static final User OWNER = user(1, "admin", "USER_VIP_0");
    private static final User DRIVER1 = user(3, "driver1", "DRIVER");
    private static final User DRIVER2 = user(4, "driver2", "DRIVER");

    @Mock
    private JobDriverRequestRepository requestRepository;
    @Mock
    private DeliveryJobRepository deliveryJobRepository;
    @Mock
    private DeliveryRoutingRepository deliveryRoutingRepository;
    @Mock
    private TeamMemberRepository teamMemberRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private JobAccessService jobAccessService;
    @Mock
    private TeamNotifier teamNotifier;
    @Mock
    private MasterDataService masterDataService;
    @Mock
    private DeliveryJobEventRepository deliveryJobEventRepository;

    private JobDriverRequestService service;

    @BeforeEach
    void setUp() {
        service = new JobDriverRequestService(requestRepository, deliveryJobRepository, deliveryRoutingRepository,
                teamMemberRepository, userRepository, jobAccessService, teamNotifier, masterDataService, CLOCK,
                new JobRequestPolicy(24, 8, 2, 10), deliveryJobEventRepository);
        lenient().when(userRepository.findByUsername("admin")).thenReturn(Optional.of(OWNER));
        lenient().when(userRepository.findByUsername("driver1")).thenReturn(Optional.of(DRIVER1));
        lenient().when(userRepository.findByUsername("driver2")).thenReturn(Optional.of(DRIVER2));
        lenient().when(userRepository.findById(1)).thenReturn(Optional.of(OWNER));
        lenient().when(userRepository.findById(3)).thenReturn(Optional.of(DRIVER1));
        lenient().when(userRepository.findById(4)).thenReturn(Optional.of(DRIVER2));
    }

    private static User user(int id, String username, String roleName) {
        Role role = new Role();
        role.setRoleName(roleName);
        User user = new User();
        user.setUserId(id);
        user.setUsername(username);
        user.setRole(role);
        return user;
    }

    private static DeliveryJob job(String deliveryStatus, Integer driverId) {
        return DeliveryJob.builder().jobId(10L).jobCode("JOB-10").jobName("รอบเช้า").status("A")
                .deliveryStatus(deliveryStatus).createdBy("admin").assignedDriverId(driverId)
                .originProvinceCode("47").originDistrictCode("4701").build();
    }

    private static JobDriverRequest request(long id, Integer driverId, String status) {
        return JobDriverRequest.builder().requestId(id).jobId(10L).ownerUserId(1).driverUserId(driverId)
                .requestStatus(status).requestedAt(NOW.minusHours(1)).build();
    }

    // ---- requestDriver (owner) ----

    @Test
    void should_askDriverAndKeepJobUnassigned_when_ownerPicksActiveMemberOfNewJob() {
        //GIVEN
        DeliveryJob job = job("NEW", null);
        when(requestRepository.findPendingForUpdate(10L)).thenReturn(Optional.empty());
        when(jobAccessService.lockActiveMember("admin", 3)).thenReturn(DRIVER1);
        when(requestRepository.insertPending(10L, 1, 3, NOW, "admin")).thenReturn(1);
        //WHEN
        service.requestDriver(job, 3, "admin", 5);
        //THEN
        assertThat(job.getAssignedDriverId()).isNull();
        assertThat(job.getUpdatedBy()).isEqualTo("admin");
        verify(teamNotifier).jobRequested(job, OWNER, DRIVER1, 5);
        verify(jobAccessService, never()).notifyUnassigned(any(), any());
    }

    @Test
    void should_unassignAcceptedDriverAndAskNewOne_when_ownerPicksSomeoneElse() {
        //GIVEN — G1: the driver who accepted loses the job at once
        DeliveryJob job = job("NEW", 3);
        when(requestRepository.findPendingForUpdate(10L)).thenReturn(Optional.empty());
        when(jobAccessService.lockActiveMember("admin", 4)).thenReturn(DRIVER2);
        when(requestRepository.insertPending(10L, 1, 4, NOW, "admin")).thenReturn(1);
        //WHEN
        service.requestDriver(job, 4, "admin", 2);
        //THEN
        assertThat(job.getAssignedDriverId()).isNull();
        verify(jobAccessService).notifyUnassigned(job, 3);
        verify(teamNotifier).jobRequested(job, OWNER, DRIVER2, 2);
    }

    @Test
    void should_cancelWaitingRequestAndAskNewOne_when_ownerChangesMindBeforeAnswer() {
        //GIVEN — G2: one pending request per job
        DeliveryJob job = job("NEW", null);
        JobDriverRequest waiting = request(5L, 3, JobDriverRequestStatus.PENDING);
        when(requestRepository.findPendingForUpdate(10L)).thenReturn(Optional.of(waiting));
        when(jobAccessService.lockActiveMember("admin", 4)).thenReturn(DRIVER2);
        when(requestRepository.insertPending(10L, 1, 4, NOW, "admin")).thenReturn(1);
        //WHEN
        service.requestDriver(job, 4, "admin", 2);
        //THEN
        assertThat(waiting.getRequestStatus()).isEqualTo(JobDriverRequestStatus.CANCELLED);
        assertThat(waiting.getUpdatedBy()).isEqualTo("admin");
        verify(requestRepository).saveAndFlush(waiting);
        verify(teamNotifier).jobRequestCancelled(job, DRIVER1);
        verify(teamNotifier).jobRequested(job, OWNER, DRIVER2, 2);
    }

    @Test
    void should_changeNothing_when_ownerPicksDriverWhoAcceptedOrIsAlreadyAsked() {
        //GIVEN
        DeliveryJob accepted = job("IN_PROGRESS", 3);
        DeliveryJob asked = job("NEW", null);
        when(requestRepository.findPendingForUpdate(10L))
                .thenReturn(Optional.empty(), Optional.of(request(5L, 4, JobDriverRequestStatus.PENDING)));
        //WHEN
        service.requestDriver(accepted, 3, "admin", 1);
        service.requestDriver(asked, 4, "admin", 1);
        //THEN
        verify(requestRepository, never()).insertPending(anyLong(), anyInt(), anyInt(), any(), anyString());
        verify(deliveryJobRepository, never()).save(any());
        verifyNoInteractions(teamNotifier);
    }

    @Test
    void should_cancelRequestWithoutAskingAnyone_when_ownerPicksNobody() {
        //GIVEN — G10: no consent needed to take a job away
        DeliveryJob job = job("NEW", null);
        JobDriverRequest waiting = request(5L, 3, JobDriverRequestStatus.PENDING);
        when(requestRepository.findPendingForUpdate(10L)).thenReturn(Optional.of(waiting));
        //WHEN
        service.requestDriver(job, null, "admin", 1);
        //THEN
        assertThat(waiting.getRequestStatus()).isEqualTo(JobDriverRequestStatus.CANCELLED);
        verify(teamNotifier).jobRequestCancelled(job, DRIVER1);
        verify(jobAccessService, never()).lockActiveMember(anyString(), anyInt());
        verify(requestRepository, never()).insertPending(anyLong(), anyInt(), anyInt(), any(), anyString());
    }

    @Test
    void should_changeNothing_when_ownerUnassignsJobWithoutDriverOrRequest() {
        //GIVEN
        DeliveryJob job = job("IN_PROGRESS", null);
        when(requestRepository.findPendingForUpdate(10L)).thenReturn(Optional.empty());
        //WHEN
        service.requestDriver(job, null, "admin", 1);
        //THEN
        verify(deliveryJobRepository, never()).save(any());
        verifyNoInteractions(teamNotifier);
    }

    @Test
    void should_refuse_when_jobHasStarted() {
        //GIVEN — G3
        DeliveryJob job = job("IN_PROGRESS", 3);
        when(requestRepository.findPendingForUpdate(10L)).thenReturn(Optional.empty());
        //WHEN / THEN
        assertThatThrownBy(() -> service.requestDriver(job, 4, "admin", 1))
                .isInstanceOf(IllegalArgumentException.class).hasMessage(JobDriverRequestService.JOB_STARTED);
        assertThat(job.getAssignedDriverId()).isEqualTo(3);
        verifyNoInteractions(teamNotifier);
    }

    @Test
    void should_keepWaitingRequest_when_newDriverIsNotActiveMember() {
        //GIVEN — the membership check runs before anything is cancelled
        DeliveryJob job = job("NEW", null);
        JobDriverRequest waiting = request(5L, 3, JobDriverRequestStatus.PENDING);
        when(requestRepository.findPendingForUpdate(10L)).thenReturn(Optional.of(waiting));
        when(jobAccessService.lockActiveMember("admin", 9))
                .thenThrow(new IllegalArgumentException(JobAccessService.NOT_A_MEMBER));
        //WHEN / THEN
        assertThatThrownBy(() -> service.requestDriver(job, 9, "admin", 1))
                .isInstanceOf(IllegalArgumentException.class).hasMessage(JobAccessService.NOT_A_MEMBER);
        assertThat(waiting.getRequestStatus()).isEqualTo(JobDriverRequestStatus.PENDING);
        verifyNoInteractions(teamNotifier);
    }

    @Test
    void should_refuseWithoutNotifying_when_anotherRequestWasCreatedAtTheSameTime() {
        //GIVEN — the partial unique index kept the other one (insert returned 0)
        DeliveryJob job = job("NEW", null);
        when(requestRepository.findPendingForUpdate(10L)).thenReturn(Optional.empty());
        when(jobAccessService.lockActiveMember("admin", 3)).thenReturn(DRIVER1);
        when(requestRepository.insertPending(10L, 1, 3, NOW, "admin")).thenReturn(0);
        //WHEN / THEN
        assertThatThrownBy(() -> service.requestDriver(job, 3, "admin", 1))
                .isInstanceOf(IllegalArgumentException.class).hasMessage(JobDriverRequestService.REQUEST_BUSY);
        verify(teamNotifier, never()).jobRequested(any(), any(), any(), anyInt());
    }

    // ---- cancelPending (owner starts / deletes the job — G4) ----

    @Test
    void should_tellWaitingDriver_when_ownerStartsOrDeletesJob() {
        //GIVEN
        DeliveryJob job = job("IN_PROGRESS", null);
        JobDriverRequest waiting = request(5L, 3, JobDriverRequestStatus.PENDING);
        when(requestRepository.findPendingForUpdate(10L)).thenReturn(Optional.of(waiting));
        //WHEN
        service.cancelPending(job, "admin");
        //THEN
        assertThat(waiting.getRequestStatus()).isEqualTo(JobDriverRequestStatus.CANCELLED);
        verify(teamNotifier).jobRequestCancelled(job, DRIVER1);
    }

    // ---- accept (driver) ----

    private void givenRequestOfDriver1(DeliveryJob job, JobDriverRequest request) {
        when(requestRepository.findJobIdOfDriverRequest(5L, 3)).thenReturn(Optional.of(10L));
        lenient().when(deliveryJobRepository.findForUpdate(10L)).thenReturn(Optional.of(job));
        lenient().when(requestRepository.findForUpdate(5L)).thenReturn(Optional.of(request));
    }

    @Test
    void should_giveJobToDriverAndTellOwner_when_acceptingPendingRequest() {
        //GIVEN
        DeliveryJob job = job("NEW", null);
        JobDriverRequest pending = request(5L, 3, JobDriverRequestStatus.PENDING);
        givenRequestOfDriver1(job, pending);
        //WHEN
        DriverRequestResponse result = service.accept("driver1", 5L);
        //THEN
        assertThat(pending.getRequestStatus()).isEqualTo(JobDriverRequestStatus.ACCEPTED);
        assertThat(pending.getRespondedAt()).isEqualTo(NOW);
        assertThat(job.getAssignedDriverId()).isEqualTo(3);
        assertThat(job.getUpdatedBy()).isEqualTo("driver1");
        verify(jobAccessService).lockActiveMember("admin", 3);
        verify(teamNotifier).jobRequestAnswered(job, OWNER, DRIVER1, true, null);
        assertThat(result.getRequestStatus()).isEqualTo(JobDriverRequestStatus.ACCEPTED);
    }

    @Test
    void should_refuse_when_requestIsNoLongerPending() {
        //GIVEN — the owner picked someone else a moment before
        DeliveryJob job = job("NEW", null);
        givenRequestOfDriver1(job, request(5L, 3, JobDriverRequestStatus.CANCELLED));
        //WHEN / THEN
        assertThatThrownBy(() -> service.accept("driver1", 5L))
                .isInstanceOf(IllegalArgumentException.class).hasMessage(JobDriverRequestService.REQUEST_CLOSED);
        assertThat(job.getAssignedDriverId()).isNull();
        verifyNoInteractions(teamNotifier);
    }

    @Test
    void should_refuse_when_jobAlreadyStarted() {
        //GIVEN
        DeliveryJob job = job("IN_PROGRESS", null);
        givenRequestOfDriver1(job, request(5L, 3, JobDriverRequestStatus.PENDING));
        //WHEN / THEN
        assertThatThrownBy(() -> service.accept("driver1", 5L))
                .isInstanceOf(IllegalArgumentException.class).hasMessage(JobDriverRequestService.REQUEST_CLOSED);
        assertThat(job.getAssignedDriverId()).isNull();
    }

    @Test
    void should_refuse_when_driverIsNoLongerActiveInOwnersTeam() {
        //GIVEN
        DeliveryJob job = job("NEW", null);
        JobDriverRequest pending = request(5L, 3, JobDriverRequestStatus.PENDING);
        givenRequestOfDriver1(job, pending);
        when(jobAccessService.lockActiveMember("admin", 3))
                .thenThrow(new IllegalArgumentException(JobAccessService.NOT_A_MEMBER));
        //WHEN / THEN
        assertThatThrownBy(() -> service.accept("driver1", 5L))
                .isInstanceOf(IllegalArgumentException.class).hasMessage(JobDriverRequestService.REQUEST_CLOSED);
        assertThat(pending.getRequestStatus()).isEqualTo(JobDriverRequestStatus.PENDING);
        assertThat(job.getAssignedDriverId()).isNull();
    }

    @Test
    void should_answerNotFound_when_requestIsAddressedToAnotherDriver() {
        //GIVEN
        when(requestRepository.findJobIdOfDriverRequest(5L, 4)).thenReturn(Optional.empty());
        //WHEN / THEN
        assertThatThrownBy(() -> service.accept("driver2", 5L))
                .isInstanceOf(IllegalArgumentException.class).hasMessage(JobDriverRequestService.REQUEST_NOT_FOUND);
        verify(deliveryJobRepository, never()).findForUpdate(anyLong());
    }

    @Test
    void should_forbid_when_ownerAccountUsesDriverSide() {
        //GIVEN / WHEN / THEN
        assertThatThrownBy(() -> service.accept("admin", 5L)).isInstanceOf(PermissionDeniedException.class);
        assertThatThrownBy(() -> service.listMyRequests("admin")).isInstanceOf(PermissionDeniedException.class);
    }

    // ---- decline (driver) ----

    @Test
    void should_recordReasonAndTellOwnerWhoDeclined_when_driverDeclinesWithReason() {
        //GIVEN
        DeliveryJob job = job("NEW", null);
        JobDriverRequest pending = request(5L, 3, JobDriverRequestStatus.PENDING);
        givenRequestOfDriver1(job, pending);
        when(deliveryJobRepository.findById(10L)).thenReturn(Optional.of(job));
        //WHEN
        service.decline("driver1", 5L, new DeclineRequest("TOO_FAR", null));
        //THEN
        assertThat(pending.getRequestStatus()).isEqualTo(JobDriverRequestStatus.DECLINED);
        assertThat(pending.getDeclineReason()).isEqualTo("TOO_FAR");
        assertThat(pending.getRespondedAt()).isEqualTo(NOW);
        assertThat(job.getAssignedDriverId()).isNull();
        verify(teamNotifier).jobRequestAnswered(job, OWNER, DRIVER1, false, "ไกลเกินไป");
    }

    @Test
    void should_declineWithoutReason_when_bodyIsEmpty() {
        //GIVEN
        DeliveryJob job = job("NEW", null);
        JobDriverRequest pending = request(5L, 3, JobDriverRequestStatus.PENDING);
        givenRequestOfDriver1(job, pending);
        when(deliveryJobRepository.findById(10L)).thenReturn(Optional.of(job));
        //WHEN
        service.decline("driver1", 5L, null);
        //THEN
        assertThat(pending.getRequestStatus()).isEqualTo(JobDriverRequestStatus.DECLINED);
        assertThat(pending.getDeclineReason()).isNull();
        verify(teamNotifier).jobRequestAnswered(eq(job), eq(OWNER), eq(DRIVER1), eq(false), isNull());
    }

    @Test
    void should_tellOwnerTheTypedNote_when_reasonIsOther() {
        //GIVEN
        DeliveryJob job = job("NEW", null);
        JobDriverRequest pending = request(5L, 3, JobDriverRequestStatus.PENDING);
        givenRequestOfDriver1(job, pending);
        when(deliveryJobRepository.findById(10L)).thenReturn(Optional.of(job));
        //WHEN
        service.decline("driver1", 5L, new DeclineRequest("OTHER", "  ฝนตกหนัก ถนนปิด "));
        //THEN
        assertThat(pending.getDeclineNote()).isEqualTo("ฝนตกหนัก ถนนปิด");
        verify(teamNotifier).jobRequestAnswered(job, OWNER, DRIVER1, false, "ฝนตกหนัก ถนนปิด");
    }

    @Test
    void should_refuseBeforeTouchingRequest_when_noteComesWithOtherReasonOrReasonIsUnknown() {
        //GIVEN / WHEN / THEN
        assertThatThrownBy(() -> service.decline("driver1", 5L, new DeclineRequest("TOO_FAR", "ไกลมาก")))
                .isInstanceOf(IllegalArgumentException.class).hasMessage(JobDriverRequestService.NOTE_ONLY_OTHER);
        assertThatThrownBy(() -> service.decline("driver1", 5L, new DeclineRequest("LAZY", null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage(JobDriverRequestService.INVALID_REASON);
        verify(requestRepository, never()).findForUpdate(anyLong());
    }

    @Test
    void should_refuse_when_requestWasAlreadyAnswered() {
        //GIVEN
        givenRequestOfDriver1(job("NEW", 3), request(5L, 3, JobDriverRequestStatus.ACCEPTED));
        //WHEN / THEN
        assertThatThrownBy(() -> service.decline("driver1", 5L, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessage(JobDriverRequestService.REQUEST_CLOSED);
        verifyNoInteractions(teamNotifier);
    }

    // ---- describe (owner view) ----

    @Test
    void should_showPendingAndLatestDeclineOnlyWhileUnassigned_when_describingOwnerJobs() {
        //GIVEN
        DeliveryJob waiting = DeliveryJob.builder().jobId(1L).build();
        DeliveryJob declinedNoDriver = DeliveryJob.builder().jobId(2L).build();
        DeliveryJob declinedThenAssigned = DeliveryJob.builder().jobId(3L).assignedDriverId(4).build();
        JobDriverRequest pending = request(11L, 3, JobDriverRequestStatus.PENDING);
        pending.setJobId(1L);
        JobDriverRequest declined = request(12L, 3, JobDriverRequestStatus.DECLINED);
        declined.setJobId(2L);
        declined.setDeclineReason("VEHICLE");
        JobDriverRequest oldDecline = request(13L, 3, JobDriverRequestStatus.DECLINED);
        oldDecline.setJobId(3L);
        when(requestRepository.findLatestByJobIds(List.of(1L, 2L, 3L))).thenReturn(List.of(pending, declined, oldDecline));
        when(requestRepository.findDeclinedDrivers(List.of(1L, 2L, 3L))).thenReturn(List.of(jobDriver(2L, 3)));
        when(userRepository.findAllById(any())).thenReturn(List.of(DRIVER1));
        //WHEN
        Map<Long, JobDriverRequestService.JobRequests> result =
                service.describe(List.of(waiting, declinedNoDriver, declinedThenAssigned));
        //THEN
        assertThat(result.get(1L).pending().getDriver().getUsername()).isEqualTo("driver1");
        assertThat(result.get(1L).lastDeclined()).isNull();
        assertThat(result.get(2L).lastDeclined().getDeclineReasonText()).isEqualTo("รถไม่พร้อม");
        assertThat(result.get(2L).declinedDriverIds()).containsExactly(3);
        assertThat(result).doesNotContainKey(3L);
    }

    @Test
    void should_returnEmptyWithoutQuerying_when_noJobs() {
        //GIVEN / WHEN
        Map<Long, JobDriverRequestService.JobRequests> result = service.describe(List.of());
        //THEN
        assertThat(result).isEmpty();
        verifyNoInteractions(requestRepository);
    }

    private static JobDriverRequestRepository.JobDriver jobDriver(Long jobId, Integer driverId) {
        return new JobDriverRequestRepository.JobDriver() {
            @Override
            public Long getJobId() {
                return jobId;
            }

            @Override
            public Integer getDriverUserId() {
                return driverId;
            }
        };
    }

    // ---- listMyRequests (driver) ----

    private static DeliveryRouting stop(DeliveryJob job, int seq, String routingStatus, String districtCode) {
        OrderAddress address = OrderAddress.builder().provinceCode("47").districtCode(districtCode)
                .addressLine("12 บ้านลูกค้า").build();
        Order order = Order.builder().orderId((long) seq).address(address).build();
        return DeliveryRouting.builder().deliveryJob(job).order(order).sequenceNo(seq).routingStatus(routingStatus)
                .build();
    }

    @Test
    void should_listSummaryOfActiveTeamsOnly_when_driverListsRequests() {
        //GIVEN — owner2 (id 2) removed driver1, so its request is hidden
        DeliveryJob job = job("NEW", null);
        JobDriverRequest fromAdmin = request(5L, 3, JobDriverRequestStatus.PENDING);
        JobDriverRequest fromOwner2 = request(6L, 3, JobDriverRequestStatus.PENDING);
        fromOwner2.setOwnerUserId(2);
        fromOwner2.setJobId(20L);
        when(requestRepository.findByDriverUserIdAndRequestStatusOrderByRequestedAtDesc(3, "PENDING"))
                .thenReturn(List.of(fromAdmin, fromOwner2));
        when(teamMemberRepository.findByOwnerUserIdInAndMemberUserIdInAndMemberStatusIn(any(), eq(List.of(3)),
                eq(List.of(TeamMemberStatus.ACTIVE))))
                .thenReturn(List.of(TeamMember.builder().ownerUserId(1).memberUserId(3).build()));
        when(deliveryJobRepository.findAllById(List.of(10L))).thenReturn(List.of(job));
        when(deliveryRoutingRepository.findByDeliveryJobJobIdIn(List.of(10L))).thenReturn(List.of(
                stop(job, 1, "NEW", "4701"), stop(job, 2, "NEW", "4709"), stop(job, 3, "NEW", "4701"),
                stop(job, 4, "CANCELLED", "4712")));
        when(userRepository.findAllById(any())).thenReturn(List.of(OWNER));
        when(masterDataService.getAddressInfoSafe("47", "4701", null)).thenReturn(
                AddressInfoDto.builder().districtName("เมืองสกลนคร").provinceName("สกลนคร").build());
        when(masterDataService.getAddressInfoSafe("47", "4709", null)).thenReturn(
                AddressInfoDto.builder().districtName("พรรณานิคม").provinceName("สกลนคร").build());
        //WHEN
        List<DriverRequestResponse> result = service.listMyRequests("driver1");
        //THEN — cancelled stops are not counted; areas are distinct, in route order; no customer data exists
        assertThat(result).hasSize(1);
        DriverRequestResponse summary = result.getFirst();
        assertThat(summary.getRequestId()).isEqualTo(5L);
        assertThat(summary.getStopCount()).isEqualTo(3);
        assertThat(summary.getStopAreas()).containsExactly("อ.เมืองสกลนคร จ.สกลนคร", "อ.พรรณานิคม จ.สกลนคร");
        assertThat(summary.getOriginText()).isEqualTo("อ.เมืองสกลนคร จ.สกลนคร");
        assertThat(summary.getOwner().getUsername()).isEqualTo("admin");
        assertThat(summary.toString()).doesNotContain("บ้านลูกค้า");
    }

    @Test
    void should_hideRequest_when_jobAlreadyStarted() {
        //GIVEN
        DeliveryJob started = job("IN_PROGRESS", null);
        when(requestRepository.findByDriverUserIdAndRequestStatusOrderByRequestedAtDesc(3, "PENDING"))
                .thenReturn(List.of(request(5L, 3, JobDriverRequestStatus.PENDING)));
        when(teamMemberRepository.findByOwnerUserIdInAndMemberUserIdInAndMemberStatusIn(any(), any(), any()))
                .thenReturn(List.of(TeamMember.builder().ownerUserId(1).memberUserId(3).build()));
        when(deliveryJobRepository.findAllById(List.of(10L))).thenReturn(List.of(started));
        //WHEN
        List<DriverRequestResponse> result = service.listMyRequests("driver1");
        //THEN
        assertThat(result).isEmpty();
    }

    @Test
    void should_returnEmpty_when_driverHasNoRequests() {
        //GIVEN
        when(requestRepository.findByDriverUserIdAndRequestStatusOrderByRequestedAtDesc(3, "PENDING"))
                .thenReturn(List.of());
        //WHEN
        List<DriverRequestResponse> result = service.listMyRequests("driver1");
        //THEN
        assertThat(result).isEmpty();
        verifyNoInteractions(teamMemberRepository);
    }

    // ---- รับงานซ้อนวัน (driver-risk ข้อ 7) ----

    private void givenOneVisibleRequest(DeliveryJob requested) {
        when(requestRepository.findByDriverUserIdAndRequestStatusOrderByRequestedAtDesc(3, "PENDING"))
                .thenReturn(List.of(request(5L, 3, JobDriverRequestStatus.PENDING)));
        when(teamMemberRepository.findByOwnerUserIdInAndMemberUserIdInAndMemberStatusIn(any(), any(), any()))
                .thenReturn(List.of(TeamMember.builder().ownerUserId(1).memberUserId(3).build()));
        when(deliveryJobRepository.findAllById(List.of(10L))).thenReturn(List.of(requested));
        // จุดของงานที่ขอ / ชื่อพื้นที่ ไม่ใช่เรื่องที่ทดสอบ
        lenient().when(deliveryRoutingRepository.findByDeliveryJobJobIdIn(List.of(10L))).thenReturn(List.of());
        lenient().when(masterDataService.getAddressInfoSafe(any(), any(), any()))
                .thenReturn(AddressInfoDto.builder().build());
    }

    private static DeliveryJob myJob(long id, String deliveryStatus, LocalDateTime scheduled) {
        return DeliveryJob.builder().jobId(id).status("A").deliveryStatus(deliveryStatus).createdBy("owner2")
                .assignedDriverId(3).scheduledDate(scheduled).build();
    }

    @Test
    void should_countMyOpenJobsAndStopsOnTheSameDay_when_listingRequests() {
        //GIVEN — งานที่ขอ นัด 10 พ.ย. 09:00 · งานของฉัน: 10 พ.ย. 17:00 (นับ), 10 พ.ย. ปิดแล้ว (ไม่นับ),
        //        11 พ.ย. (คนละวัน), ไม่มีวันนัด (ไม่นับ)
        DeliveryJob requested = job("NEW", null);
        requested.setScheduledDate(LocalDateTime.of(2026, 11, 10, 9, 0));
        givenOneVisibleRequest(requested);
        DeliveryJob sameDay = myJob(31L, "IN_PROGRESS", LocalDateTime.of(2026, 11, 10, 17, 0));
        DeliveryJob sameDayClosed = myJob(32L, "COMPLETED", LocalDateTime.of(2026, 11, 10, 8, 0));
        DeliveryJob nextDay = myJob(33L, "NEW", LocalDateTime.of(2026, 11, 11, 0, 0));
        DeliveryJob noDate = myJob(34L, "NEW", null);
        when(deliveryJobRepository.findAssignedToDriver(3)).thenReturn(List.of(sameDay, sameDayClosed, nextDay, noDate));
        lenient().when(deliveryRoutingRepository.findByDeliveryJobJobIdIn(List.of(31L, 33L))).thenReturn(List.of(
                stop(sameDay, 1, "NEW", "4701"), stop(sameDay, 2, "COMPLETED", "4701"),
                stop(sameDay, 3, "CANCELLED", "4701"), stop(nextDay, 1, "NEW", "4701")));

        //WHEN
        List<DriverRequestResponse> result = service.listMyRequests("driver1");

        //THEN — จุดที่ยกเลิกไม่นับ
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getSameDayJobCount()).isEqualTo(1);
        assertThat(result.getFirst().getSameDayStopCount()).isEqualTo(2);
    }

    @Test
    void should_reportNoSameDayJobs_when_requestHasNoDate() {
        //GIVEN — boundary: งานที่ขอไม่มีวันนัด เทียบวันไม่ได้
        givenOneVisibleRequest(job("NEW", null));
        when(deliveryJobRepository.findAssignedToDriver(3)).thenReturn(
                List.of(myJob(31L, "NEW", LocalDateTime.of(2026, 11, 10, 9, 0))));

        //WHEN
        List<DriverRequestResponse> result = service.listMyRequests("driver1");

        //THEN
        assertThat(result.getFirst().getSameDayJobCount()).isZero();
        assertThat(result.getFirst().getSameDayStopCount()).isZero();
    }

    // ---- คำขอหมดอายุ / คืนงาน (driver-risk ข้อ 16) ----

    @Test
    void should_refuseWithoutSaving_when_acceptingRequestPastItsExpiry() {
        //GIVEN — ส่งคำขอมา 25 ชม. แล้ว (เกิน 24 ชม.) ตัวตั้งเวลายังไม่ทันเปลี่ยนเป็น EXPIRED
        DeliveryJob job = job("NEW", null);
        JobDriverRequest pending = request(5L, 3, JobDriverRequestStatus.PENDING);
        pending.setRequestedAt(NOW.minusHours(25));
        givenRequestOfDriver1(job, pending);
        //WHEN / THEN
        assertThatThrownBy(() -> service.accept("driver1", 5L))
                .isInstanceOf(IllegalArgumentException.class).hasMessage(JobDriverRequestService.REQUEST_EXPIRED);
        assertThat(pending.getRequestStatus()).isEqualTo(JobDriverRequestStatus.PENDING);
        assertThat(job.getAssignedDriverId()).isNull();
        verify(requestRepository, never()).save(any());
    }

    @Test
    void should_expireOnlyOverdueStillPendingRequestsAndTellOwner_when_expiryRuns() {
        //GIVEN — overdue (25 ชม.), ยังไม่ครบ (1 ชม.), และอันที่คนส่งเพิ่งตอบไปก่อนล็อก
        DeliveryJob job = job("NEW", null);
        JobDriverRequest overdue = request(5L, 3, JobDriverRequestStatus.PENDING);
        overdue.setRequestedAt(NOW.minusHours(25));
        JobDriverRequest fresh = request(6L, 4, JobDriverRequestStatus.PENDING);
        JobDriverRequest answeredMeanwhile = request(7L, 3, JobDriverRequestStatus.PENDING);
        answeredMeanwhile.setRequestedAt(NOW.minusHours(30));
        JobDriverRequest lockedAnswered = request(7L, 3, JobDriverRequestStatus.ACCEPTED);
        lockedAnswered.setRequestedAt(NOW.minusHours(30));
        when(requestRepository.findByRequestStatus("PENDING")).thenReturn(List.of(overdue, fresh, answeredMeanwhile));
        when(deliveryJobRepository.findForUpdate(10L)).thenReturn(Optional.of(job));
        when(requestRepository.findForUpdate(5L)).thenReturn(Optional.of(overdue));
        when(requestRepository.findForUpdate(6L)).thenReturn(Optional.of(fresh));
        when(requestRepository.findForUpdate(7L)).thenReturn(Optional.of(lockedAnswered));
        //WHEN
        int expired = service.expireOverdue();
        //THEN
        assertThat(expired).isEqualTo(1);
        assertThat(overdue.getRequestStatus()).isEqualTo(JobDriverRequestStatus.EXPIRED);
        assertThat(overdue.getUpdatedBy()).isEqualTo("system");
        assertThat(fresh.getRequestStatus()).isEqualTo(JobDriverRequestStatus.PENDING);
        assertThat(lockedAnswered.getRequestStatus()).isEqualTo(JobDriverRequestStatus.ACCEPTED);
        verify(teamNotifier).jobRequestExpired(eq(job), eq(new TeamNotifier.JobDriverRequestKey(5L)), eq(OWNER),
                eq(DRIVER1));
        verify(requestRepository).save(overdue);
    }

    @Test
    void should_hideTimeExpiredRequestAndTellExpiry_when_driverListsRequests() {
        //GIVEN — งานนัด 3 พ.ย. → หมดอายุ 08:00 วันนั้น (ก่อนครบ 24 ชม.)
        DeliveryJob job = job("NEW", null);
        job.setScheduledDate(LocalDateTime.of(2026, 11, 3, 0, 0));
        JobDriverRequest pending = request(5L, 3, JobDriverRequestStatus.PENDING);
        JobDriverRequest overdue = request(6L, 3, JobDriverRequestStatus.PENDING);
        overdue.setRequestedAt(NOW.minusHours(30));
        when(requestRepository.findByDriverUserIdAndRequestStatusOrderByRequestedAtDesc(3, "PENDING"))
                .thenReturn(List.of(pending, overdue));
        when(teamMemberRepository.findByOwnerUserIdInAndMemberUserIdInAndMemberStatusIn(any(), any(), any()))
                .thenReturn(List.of(TeamMember.builder().ownerUserId(1).memberUserId(3).build()));
        when(deliveryJobRepository.findAllById(any())).thenReturn(List.of(job));
        lenient().when(masterDataService.getAddressInfoSafe(any(), any(), any()))
                .thenReturn(AddressInfoDto.builder().build());
        //WHEN
        List<DriverRequestResponse> result = service.listMyRequests("driver1");
        //THEN
        assertThat(result).extracting(DriverRequestResponse::getRequestId).containsExactly(5L);
        assertThat(result.getFirst().getExpiresAt()).isEqualTo(LocalDateTime.of(2026, 11, 3, 8, 0));
    }

    @Test
    void should_showWhyDriverIsMissing_when_latestRequestExpiredOrWasReleased() {
        //GIVEN
        DeliveryJob job = job("NEW", null);
        JobDriverRequest released = request(5L, 3, JobDriverRequestStatus.RELEASED);
        released.setDeclineReason("VEHICLE");
        when(requestRepository.findLatestByJobIds(List.of(10L))).thenReturn(List.of(released));
        when(requestRepository.findDeclinedDrivers(List.of(10L))).thenReturn(List.of());
        when(userRepository.findAllById(any())).thenReturn(List.of(DRIVER1));
        //WHEN
        JobDriverRequestService.JobRequests requests = service.describe(List.of(job)).get(10L);
        //THEN — คืนงาน ไม่นับเป็น "ปฏิเสธงานนี้ไปแล้ว"
        assertThat(requests.lastDeclined().getRequestStatus()).isEqualTo(JobDriverRequestStatus.RELEASED);
        assertThat(requests.lastDeclined().getDeclineReasonText()).isEqualTo("รถไม่พร้อม");
        assertThat(requests.pending()).isNull();
        assertThat(requests.declinedDriverIds()).isEmpty();
    }

    private DeliveryJob givenMyAcceptedJob(String deliveryStatus) {
        DeliveryJob job = job(deliveryStatus, 3);
        when(deliveryJobRepository.findForUpdate(10L)).thenReturn(Optional.of(job));
        lenient().when(jobAccessService.isAssignedActiveDriver(job, "driver1")).thenReturn(true);
        return job;
    }

    @Test
    void should_unassignMarkReleasedRecordAndTellOwner_when_driverGivesBackNotStartedJob() {
        //GIVEN
        DeliveryJob job = givenMyAcceptedJob("NEW");
        JobDriverRequest accepted = request(5L, 3, JobDriverRequestStatus.ACCEPTED);
        when(requestRepository.findFirstByJobIdAndDriverUserIdAndRequestStatusOrderByRequestIdDesc(10L, 3, "ACCEPTED"))
                .thenReturn(Optional.of(accepted));
        //WHEN
        service.release("driver1", 10L, new DeclineRequest("OTHER", " ป่วย "));
        //THEN
        assertThat(job.getAssignedDriverId()).isNull();
        assertThat(accepted.getRequestStatus()).isEqualTo(JobDriverRequestStatus.RELEASED);
        assertThat(accepted.getDeclineNote()).isEqualTo("ป่วย");
        assertThat(accepted.getRespondedAt()).isEqualTo(NOW);
        verify(deliveryJobEventRepository).save(argThat(e -> "JOB_RELEASED".equals(e.getEventType())
                && "OTHER".equals(e.getReason()) && "driver1".equals(e.getActorUsername())));
        verify(teamNotifier).jobReleased(job, OWNER, DRIVER1, "ป่วย");
    }

    @Test
    void should_refuseRelease_when_jobHasStarted() {
        //GIVEN
        DeliveryJob job = givenMyAcceptedJob("IN_PROGRESS");
        //WHEN / THEN
        assertThatThrownBy(() -> service.release("driver1", 10L, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessage(JobDriverRequestService.RELEASE_STARTED);
        assertThat(job.getAssignedDriverId()).isEqualTo(3);
        verifyNoInteractions(teamNotifier, deliveryJobEventRepository);
    }

    @Test
    void should_answerJobNotFound_when_releasingJobThatIsNotMine() {
        //GIVEN — งานของคนส่งคนอื่น / ออกจากทีมไปแล้ว
        DeliveryJob job = job("NEW", 4);
        when(deliveryJobRepository.findForUpdate(10L)).thenReturn(Optional.of(job));
        //WHEN / THEN
        assertThatThrownBy(() -> service.release("driver1", 10L, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Job not found: 10");
        assertThat(job.getAssignedDriverId()).isEqualTo(4);
    }

    @Test
    void should_refuseReleaseBeforeLocking_when_noteComesWithoutOtherReason() {
        //WHEN / THEN
        assertThatThrownBy(() -> service.release("driver1", 10L, new DeclineRequest("TOO_FAR", "ไกล")))
                .isInstanceOf(IllegalArgumentException.class).hasMessage(JobDriverRequestService.NOTE_ONLY_OTHER);
        verify(deliveryJobRepository, never()).findForUpdate(anyLong());
    }
}
