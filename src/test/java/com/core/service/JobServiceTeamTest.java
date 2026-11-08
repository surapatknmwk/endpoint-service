package com.core.service;

import com.core.constant.JobAccess;
import com.core.dto.JobResponse;
import com.core.dto.TeamUserSummary;
import com.core.dto.master.AddressInfoDto;
import com.core.entity.DeliveryJob;
import com.core.entity.DeliveryRouting;
import com.core.entity.Customer;
import com.core.entity.Order;
import com.core.entity.OrderAddress;
import com.authen.repository.UserRepository;
import com.core.dto.StopActionRequest;
import com.core.repository.DeliveryJobEventRepository;
import com.core.repository.DeliveryJobRepository;
import com.core.service.notification.TeamNotifier;
import com.core.repository.DeliveryRoutingRepository;
import com.core.repository.OrderRepository;
import com.core.service.clients.MasterDataService;
import com.core.service.team.JobAccessService;
import com.core.service.team.JobDriverRequestService;
import com.master.repository.SubdistrictDistanceRepository;
import com.master.repository.SubdistrictRepository;
import com.master.service.GoogleRoutesService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the team side of {@link JobService} (design §4.2, §5.2, §5.3; US-TEAM-05, 06, 07).
 * <ul>
 *   <li>WHAT — assignDriver locks the job and hands the choice to {@link JobDriverRequestService} (rules there);
 *   starting / deleting cancels a waiting request; the assigned driver may start, record stops and close; listJobs
 *   adds the driver's jobs; myAccess, price and request info follow the viewer.</li>
 *   <li>WHEN — assign only by the owner; operate only while
 *   {@link JobAccessService#isAssignedActiveDriver} holds; price for a driver only when the owner allows it.</li>
 *   <li>THEN — the job's driver / statuses / updated_by, the "not found" answer for anyone else, and the response
 *   fields. Status rules of the stops themselves are covered by JobServiceTest and do not change for drivers.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class JobServiceTeamTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 1, 10, 0);
    private static final Clock CLOCK = Clock.fixed(NOW.atZone(ZoneId.of("Asia/Bangkok")).toInstant(),
            ZoneId.of("Asia/Bangkok"));
    private static final TeamUserSummary OWNER = new TeamUserSummary(1, "admin", "สมชาย เจ้าของ", null);
    private static final TeamUserSummary DRIVER1 = new TeamUserSummary(3, "driver1", "ประยุทธ ขับดี", null);

    @Mock
    private DeliveryJobRepository deliveryJobRepository;
    @Mock
    private DeliveryRoutingRepository deliveryRoutingRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private SubdistrictRepository subdistrictRepository;
    @Mock
    private SubdistrictDistanceRepository subdistrictDistanceRepository;
    @Mock
    private MasterDataService masterDataService;
    @Mock
    private GoogleRoutesService googleRoutesService;
    @Mock
    private JobAccessService jobAccessService;
    @Mock
    private JobDriverRequestService jobDriverRequestService;
    @Mock
    private DeliveryJobEventRepository deliveryJobEventRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private TeamNotifier teamNotifier;

    private JobService jobService;

    private static final StopActionRequest SKIP = new StopActionRequest("CUSTOMER_ABSENT", null);
    private static final StopActionRequest CANCEL = new StopActionRequest("CHANGED_MIND", null);

    @BeforeEach
    void setUp() {
        jobService = new JobService(deliveryJobRepository, deliveryRoutingRepository, orderRepository,
                subdistrictRepository, subdistrictDistanceRepository, masterDataService, googleRoutesService, CLOCK,
                jobAccessService, jobDriverRequestService, deliveryJobEventRepository, userRepository, teamNotifier);
        lenient().when(masterDataService.getAddressInfoSafe(any(), any(), any()))
                .thenReturn(AddressInfoDto.builder().build());
    }

    private static DeliveryJob job(long id, String deliveryStatus, Integer driverId) {
        return DeliveryJob.builder().jobId(id).jobCode("JOB-" + id).deliveryStatus(deliveryStatus).status("A")
                .createdBy("admin").assignedDriverId(driverId).createdAt(NOW.minusDays(id)).build();
    }

    private static DeliveryRouting stop(DeliveryJob job, long orderId, String routingStatus, String orderStatus) {
        Order order = Order.builder().orderId(orderId).orderCode("ORD-" + orderId).orderStatus(orderStatus)
                .price(new BigDecimal("1500.00")).status("A").createdBy("admin").build();
        return DeliveryRouting.builder().deliveryJob(job).order(order).sequenceNo((int) orderId)
                .routingStatus(routingStatus).build();
    }

    private void givenStops(DeliveryJob job, DeliveryRouting... routings) {
        for (DeliveryRouting r : routings) {
            lenient().when(deliveryRoutingRepository.findByDeliveryJobJobIdAndOrderOrderId(job.getJobId(),
                    r.getOrder().getOrderId())).thenReturn(Optional.of(r));
        }
        lenient().when(deliveryRoutingRepository.findByDeliveryJobJobIdOrderBySequenceNoAsc(job.getJobId()))
                .thenReturn(List.of(routings));
    }

    private void givenPeople(DeliveryJob job, TeamUserSummary driver, boolean driverSeesPrice) {
        lenient().when(jobAccessService.describe(List.of(job)))
                .thenReturn(Map.of(job.getJobId(), new JobAccessService.JobPeople(OWNER, driver, driverSeesPrice)));
    }

    // ---- assignDriver (rules: JobDriverRequestServiceTest) ----

    @Test
    void should_lockJobAndAskRequestService_when_ownerPicksDriver() {
        //GIVEN
        DeliveryJob job = job(1L, "NEW", null);
        when(deliveryJobRepository.findForUpdate(1L)).thenReturn(Optional.of(job));
        givenStops(job, stop(job, 10L, "NEW", "ASSIGNED"), stop(job, 11L, "CANCELLED", "CANCELLED"));
        //WHEN
        JobResponse result = jobService.assignDriver(1L, 3, false, "admin");
        //THEN — cancelled stops are not counted in the request
        verify(jobDriverRequestService).requestDriver(job, 3, "admin", 1);
        assertThat(result.getMyAccess()).isEqualTo(JobAccess.MANAGE);
    }

    @Test
    void should_answerJobNotFound_when_assignedDriverTriesToAssign() {
        //GIVEN
        DeliveryJob job = job(1L, "NEW", 3);
        when(deliveryJobRepository.findForUpdate(1L)).thenReturn(Optional.of(job));
        //WHEN / THEN
        assertThatThrownBy(() -> jobService.assignDriver(1L, null, false, "driver1"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Job not found");
        verify(jobDriverRequestService, never()).requestDriver(any(), any(), anyString(), anyInt());
    }

    @Test
    void should_cancelWaitingRequest_when_ownerStartsJobThemself() {
        //GIVEN
        DeliveryJob job = job(1L, "NEW", null);
        when(deliveryJobRepository.findById(1L)).thenReturn(Optional.of(job));
        givenStops(job, stop(job, 10L, "NEW", "ASSIGNED"));
        //WHEN
        jobService.startJob(1L, "admin");
        //THEN
        verify(jobDriverRequestService).cancelPending(job, "admin");
    }

    @Test
    void should_showRequestsToOwnerOnly_when_jobHasPendingRequest() {
        //GIVEN
        DeliveryJob job = job(1L, "NEW", null);
        givenStops(job, stop(job, 10L, "NEW", "ASSIGNED"));
        when(deliveryJobRepository.findForUpdate(1L)).thenReturn(Optional.of(job));
        JobResponse.DriverRequestInfo pending = JobResponse.DriverRequestInfo.builder().requestId(5L)
                .driver(DRIVER1).requestedAt(NOW).build();
        when(jobDriverRequestService.describe(List.of(job))).thenReturn(Map.of(1L,
                new JobDriverRequestService.JobRequests(pending, null, List.of(4))));
        //WHEN
        JobResponse result = jobService.assignDriver(1L, 3, false, "admin");
        //THEN
        assertThat(result.getDriverRequest().getDriver().getUsername()).isEqualTo("driver1");
        assertThat(result.getDeclinedDriverIds()).containsExactly(4);
        assertThat(result.getLastDeclined()).isNull();
    }

    // ---- operate ----

    @Test
    void should_letAssignedDriverCompleteStop_when_stillActiveMember() {
        //GIVEN
        DeliveryJob job = job(1L, "IN_PROGRESS", 3);
        DeliveryRouting target = stop(job, 10L, "NEW", "IN_TRANSIT");
        DeliveryRouting other = stop(job, 11L, "NEW", "IN_TRANSIT");
        givenStops(job, target, other);
        when(jobAccessService.isAssignedActiveDriver(job, "driver1")).thenReturn(true);
        //WHEN
        JobResponse result = jobService.completeStop(1L, 10L, "driver1");
        //THEN
        assertThat(target.getRoutingStatus()).isEqualTo("COMPLETED");
        assertThat(target.getUpdatedBy()).isEqualTo("driver1");
        assertThat(target.getOrder().getOrderStatus()).isEqualTo("COMPLETED");
        assertThat(target.getOrder().getUpdatedBy()).isEqualTo("driver1");
        assertThat(result.getMyAccess()).isEqualTo(JobAccess.OPERATE);
    }

    @Test
    void should_answerStopNotFound_when_driverIsNoLongerActiveMember() {
        //GIVEN
        DeliveryJob job = job(1L, "IN_PROGRESS", 3);
        DeliveryRouting target = stop(job, 10L, "NEW", "IN_TRANSIT");
        givenStops(job, target);
        when(jobAccessService.isAssignedActiveDriver(job, "driver1")).thenReturn(false);
        //WHEN / THEN
        assertThatThrownBy(() -> jobService.skipStop(1L, 10L, SKIP, "driver1"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Delivery stop not found");
        assertThat(target.getRoutingStatus()).isEqualTo("NEW");
    }

    @Test
    void should_letAssignedDriverStartJob_when_stillActiveMember() {
        //GIVEN
        DeliveryJob job = job(1L, "NEW", 3);
        DeliveryRouting open = stop(job, 10L, "NEW", "ASSIGNED");
        when(deliveryJobRepository.findById(1L)).thenReturn(Optional.of(job));
        givenStops(job, open);
        when(jobAccessService.isAssignedActiveDriver(job, "driver1")).thenReturn(true);
        //WHEN
        JobResponse result = jobService.startJob(1L, "driver1");
        //THEN
        assertThat(result.getDeliveryStatus()).isEqualTo("IN_PROGRESS");
        assertThat(open.getOrder().getOrderStatus()).isEqualTo("IN_TRANSIT");
        assertThat(job.getUpdatedBy()).isEqualTo("driver1");
    }

    @Test
    void should_letAssignedDriverCloseJob_when_stillActiveMember() {
        //GIVEN
        DeliveryJob job = job(1L, "IN_PROGRESS", 3);
        DeliveryRouting open = stop(job, 10L, "NEW", "IN_TRANSIT");
        when(deliveryJobRepository.findById(1L)).thenReturn(Optional.of(job));
        givenStops(job, open);
        when(deliveryRoutingRepository.countSkipsByOrder(any(), any())).thenReturn(List.of());
        when(jobAccessService.isAssignedActiveDriver(job, "driver1")).thenReturn(true);
        //WHEN
        jobService.closeJob(1L, "driver1");
        //THEN
        assertThat(job.getDeliveryStatus()).isEqualTo("COMPLETED");
        assertThat(open.getRoutingStatus()).isEqualTo("SKIPPED");
        assertThat(open.getOrder().getOrderStatus()).isEqualTo("PENDING");
    }

    @Test
    void should_answerJobNotFound_when_driverDeletesAssignedJob() {
        //GIVEN — manage actions are the owner's only, whatever the team says
        DeliveryJob job = job(1L, "NEW", 3);
        when(deliveryJobRepository.findById(1L)).thenReturn(Optional.of(job));
        //WHEN / THEN
        assertThatThrownBy(() -> jobService.deleteJob(1L, "driver1"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Job not found");
        assertThat(job.getStatus()).isEqualTo("A");
        verify(jobAccessService, never()).isAssignedActiveDriver(any(), anyString());
    }

    @Test
    void should_tellDriverJobIsGone_when_ownerDeletesAssignedJob() {
        //GIVEN
        DeliveryJob job = job(1L, "NEW", 3);
        when(deliveryJobRepository.findById(1L)).thenReturn(Optional.of(job));
        when(deliveryRoutingRepository.findByDeliveryJobJobId(1L)).thenReturn(List.of());
        //WHEN
        jobService.deleteJob(1L, "admin");
        //THEN
        assertThat(job.getStatus()).isEqualTo("I");
        verify(jobAccessService).notifyUnassigned(job, 3);
        verify(jobDriverRequestService).cancelPending(job, "admin");
    }

    // ---- listJobs ----

    @Test
    void should_listAssignedJobsNewestFirstWithOperateAccess_when_callerIsDriver() {
        //GIVEN
        DeliveryJob older = job(2L, "COMPLETED", 3);
        DeliveryJob newer = job(1L, "NEW", 3);
        when(deliveryJobRepository.findByStatusAndCreatedByOrderByCreatedAtDesc("A", "driver1")).thenReturn(List.of());
        when(jobAccessService.findAssignedJobs("driver1")).thenReturn(List.of(older, newer));
        when(deliveryRoutingRepository.findByDeliveryJobJobIdIn(List.of(1L, 2L))).thenReturn(List.of());
        when(jobAccessService.describe(List.of(newer, older))).thenReturn(Map.of(
                1L, new JobAccessService.JobPeople(OWNER, DRIVER1, false),
                2L, new JobAccessService.JobPeople(OWNER, DRIVER1, false)));
        //WHEN
        List<JobResponse> result = jobService.listJobs("driver1");
        //THEN
        assertThat(result).extracting(JobResponse::getJobId).containsExactly(1L, 2L);
        assertThat(result).extracting(JobResponse::getMyAccess).containsOnly(JobAccess.OPERATE);
        assertThat(result.getFirst().getOwnerUsername()).isEqualTo("admin");
        assertThat(result.getFirst().getOwnerDisplayName()).isEqualTo("สมชาย เจ้าของ");
    }

    @Test
    void should_listEachJobOnce_when_ownJobIsAlsoReturnedAsAssigned() {
        //GIVEN
        DeliveryJob own = job(1L, "NEW", null);
        when(deliveryJobRepository.findByStatusAndCreatedByOrderByCreatedAtDesc("A", "admin")).thenReturn(List.of(own));
        when(jobAccessService.findAssignedJobs("admin")).thenReturn(List.of(own));
        when(deliveryRoutingRepository.findByDeliveryJobJobIdIn(List.of(1L))).thenReturn(List.of());
        //WHEN
        List<JobResponse> result = jobService.listJobs("admin");
        //THEN
        assertThat(result).extracting(JobResponse::getJobId).containsExactly(1L);
        assertThat(result.getFirst().getMyAccess()).isEqualTo(JobAccess.MANAGE);
    }

    // ---- price ----

    @Test
    void should_hidePrice_when_driverViewsAndOwnerDidNotAllow() {
        //GIVEN
        DeliveryJob job = job(1L, "IN_PROGRESS", 3);
        DeliveryRouting target = stop(job, 10L, "NEW", "IN_TRANSIT");
        givenStops(job, target, stop(job, 11L, "NEW", "IN_TRANSIT"));
        givenPeople(job, DRIVER1, false);
        when(jobAccessService.isAssignedActiveDriver(job, "driver1")).thenReturn(true);
        //WHEN
        JobResponse result = jobService.skipStop(1L, 10L, SKIP, "driver1");
        //THEN
        assertThat(result.getRoutings()).extracting(JobResponse.RoutingItem::getPrice).containsOnlyNulls();
    }

    @Test
    void should_showPrice_when_driverViewsAndOwnerAllows() {
        //GIVEN
        DeliveryJob job = job(1L, "IN_PROGRESS", 3);
        DeliveryRouting target = stop(job, 10L, "NEW", "IN_TRANSIT");
        givenStops(job, target, stop(job, 11L, "NEW", "IN_TRANSIT"));
        givenPeople(job, DRIVER1, true);
        when(jobAccessService.isAssignedActiveDriver(job, "driver1")).thenReturn(true);
        //WHEN
        JobResponse result = jobService.skipStop(1L, 10L, SKIP, "driver1");
        //THEN
        assertThat(result.getRoutings()).extracting(JobResponse.RoutingItem::getPrice)
                .containsOnly(new BigDecimal("1500.00"));
    }

    @Test
    void should_showPrice_when_ownerViewsWhateverTheSetting() {
        //GIVEN
        DeliveryJob job = job(1L, "IN_PROGRESS", 3);
        DeliveryRouting target = stop(job, 10L, "NEW", "IN_TRANSIT");
        givenStops(job, target, stop(job, 11L, "NEW", "IN_TRANSIT"));
        givenPeople(job, DRIVER1, false);
        //WHEN
        JobResponse result = jobService.skipStop(1L, 10L, SKIP, "admin");
        //THEN
        assertThat(result.getRoutings()).extracting(JobResponse.RoutingItem::getPrice)
                .containsOnly(new BigDecimal("1500.00"));
        verify(jobAccessService, never()).isAssignedActiveDriver(any(), anyString());
    }

    // ---- phones (profile.md US-PROF-07) ----

    private void givenPhones(DeliveryJob job, boolean sameTeam) {
        lenient().when(jobAccessService.describe(List.of(job))).thenReturn(Map.of(job.getJobId(),
                new JobAccessService.JobPeople(OWNER, DRIVER1, false, "0812000002", "0891000001", sameTeam)));
    }

    @Test
    void should_giveOwnerTheDriverPhoneOnly_when_ownerViewsAndTheyShareATeam() {
        //GIVEN
        DeliveryJob job = job(1L, "IN_PROGRESS", 3);
        givenStops(job, stop(job, 10L, "NEW", "IN_TRANSIT"), stop(job, 11L, "NEW", "IN_TRANSIT"));
        givenPhones(job, true);
        //WHEN
        JobResponse result = jobService.skipStop(1L, 10L, SKIP, "admin");
        //THEN
        assertThat(result.getAssignedDriver().getPhone()).isEqualTo("0891000001");
        assertThat(result.getOwnerPhone()).isNull();
    }

    @Test
    void should_hideDriverPhone_when_driverNoLongerInOwnersTeam() {
        //GIVEN
        DeliveryJob job = job(1L, "IN_PROGRESS", 3);
        givenStops(job, stop(job, 10L, "NEW", "IN_TRANSIT"), stop(job, 11L, "NEW", "IN_TRANSIT"));
        givenPhones(job, false);
        //WHEN
        JobResponse result = jobService.skipStop(1L, 10L, SKIP, "admin");
        //THEN
        assertThat(result.getAssignedDriver().getUsername()).isEqualTo("driver1");
        assertThat(result.getAssignedDriver().getPhone()).isNull();
    }

    @Test
    void should_giveDriverTheOwnerPhoneOnly_when_assignedDriverViews() {
        //GIVEN
        DeliveryJob job = job(1L, "IN_PROGRESS", 3);
        givenStops(job, stop(job, 10L, "NEW", "IN_TRANSIT"), stop(job, 11L, "NEW", "IN_TRANSIT"));
        givenPhones(job, true);
        when(jobAccessService.isAssignedActiveDriver(job, "driver1")).thenReturn(true);
        //WHEN
        JobResponse result = jobService.skipStop(1L, 10L, SKIP, "driver1");
        //THEN
        assertThat(result.getOwnerPhone()).isEqualTo("0812000002");
        assertThat(result.getAssignedDriver().getPhone()).isNull();
    }

    // ─── ซ่อนข้อมูลติดต่อลูกค้าจากคนส่งหลังส่งเสร็จ (driver-risk ข้อ 13) ─────────────────────

    private static DeliveryRouting withContact(DeliveryRouting stop) {
        stop.getOrder().setCustomer(Customer.builder().name("ลูกค้า " + stop.getOrder().getOrderId())
                .phone("0800000" + stop.getOrder().getOrderId()).build());
        stop.getOrder().setAddress(OrderAddress.builder().addressLine("99/" + stop.getOrder().getOrderId())
                .subdistrictCode("470101").build());
        stop.getOrder().setDetail("ข้าวสาร 2 ถุง");
        return stop;
    }

    @Test
    void should_hidePhoneAddressLineAndDetailOfFinishedStops_when_driverViews() {
        //GIVEN — ส่งแล้ว / ยกเลิกแล้ว = ซ่อน · ข้ามไว้ / ยังไม่ส่ง = ยังต้องติดต่อ
        DeliveryJob job = job(1L, "IN_PROGRESS", 3);
        DeliveryRouting delivered = withContact(stop(job, 10L, "COMPLETED", "COMPLETED"));
        DeliveryRouting cancelled = withContact(stop(job, 11L, "CANCELLED", "CANCELLED"));
        DeliveryRouting skipped = withContact(stop(job, 12L, "SKIPPED", "IN_TRANSIT"));
        DeliveryRouting target = withContact(stop(job, 13L, "NEW", "IN_TRANSIT"));
        givenStops(job, delivered, cancelled, skipped, target);
        givenPeople(job, DRIVER1, false);
        when(jobAccessService.isAssignedActiveDriver(job, "driver1")).thenReturn(true);
        //WHEN
        JobResponse result = jobService.skipStop(1L, 13L, SKIP, "driver1");
        //THEN — ชื่อยังเห็นเพื่ออ้างอิง
        assertThat(result.getRoutings()).extracting(JobResponse.RoutingItem::getTel)
                .containsExactly(null, null, "080000012", "080000013");
        assertThat(result.getRoutings()).extracting(item -> item.getAddress().getAddressLine())
                .containsExactly(null, null, "99/12", "99/13");
        assertThat(result.getRoutings()).extracting(JobResponse.RoutingItem::getDetail)
                .containsExactly(null, null, "ข้าวสาร 2 ถุง", "ข้าวสาร 2 ถุง");
        assertThat(result.getRoutings()).extracting(JobResponse.RoutingItem::getCustomerName)
                .containsExactly("ลูกค้า 10", "ลูกค้า 11", "ลูกค้า 12", "ลูกค้า 13");
    }

    @Test
    void should_hideContactOfEveryStop_when_driverViewsClosedJob() {
        //GIVEN — งานปิดแล้ว จุดที่ข้ามก็ไม่ต้องติดต่อแล้ว
        DeliveryJob job = job(1L, "COMPLETED", 3);
        DeliveryRouting skipped = withContact(stop(job, 10L, "SKIPPED", "PENDING"));
        when(deliveryJobRepository.findByStatusAndCreatedByOrderByCreatedAtDesc("A", "driver1")).thenReturn(List.of());
        when(jobAccessService.findAssignedJobs("driver1")).thenReturn(List.of(job));
        when(deliveryRoutingRepository.findByDeliveryJobJobIdIn(List.of(1L))).thenReturn(List.of(skipped));
        //WHEN
        List<JobResponse> result = jobService.listJobs("driver1");
        //THEN
        JobResponse.RoutingItem item = result.getFirst().getRoutings().getFirst();
        assertThat(item.getTel()).isNull();
        assertThat(item.getAddress().getAddressLine()).isNull();
        assertThat(item.getAddress().getSubdistrictCode()).isEqualTo("470101");
    }

    @Test
    void should_showFullContactOfFinishedStops_when_ownerViews() {
        //GIVEN
        DeliveryJob job = job(1L, "IN_PROGRESS", 3);
        DeliveryRouting delivered = withContact(stop(job, 10L, "COMPLETED", "COMPLETED"));
        DeliveryRouting target = withContact(stop(job, 11L, "NEW", "IN_TRANSIT"));
        givenStops(job, delivered, target);
        givenPeople(job, DRIVER1, false);
        //WHEN
        JobResponse result = jobService.skipStop(1L, 11L, SKIP, "admin");
        //THEN
        assertThat(result.getRoutings()).extracting(JobResponse.RoutingItem::getTel)
                .containsExactly("080000010", "080000011");
        assertThat(result.getRoutings().getFirst().getAddress().getAddressLine()).isEqualTo("99/10");
    }
}
