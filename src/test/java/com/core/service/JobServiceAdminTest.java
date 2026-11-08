package com.core.service;

import com.authen.repository.UserRepository;
import com.core.constant.JobEventType;
import com.core.entity.DeliveryJob;
import com.core.entity.DeliveryJobEvent;
import com.core.entity.DeliveryRouting;
import com.core.entity.Order;
import com.core.repository.DeliveryJobEventRepository;
import com.core.repository.DeliveryJobRepository;
import com.core.repository.DeliveryRoutingRepository;
import com.core.repository.OrderRepository;
import com.core.service.clients.MasterDataService;
import com.core.service.notification.TeamNotifier;
import com.core.service.team.JobAccessService;
import com.core.service.team.JobDriverRequestService;
import com.master.repository.SubdistrictDistanceRepository;
import com.master.repository.SubdistrictRepository;
import com.master.service.GoogleRoutesService;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the admin side of {@link JobService} (admin.md US-ADM-04).
 * <ul>
 *   <li>WHAT — resetJobAsAdmin moves a started / closed job back to NEW: every stop NEW without a reason / note, every
 *   order ASSIGNED without a delivery date and with its skip counter restarted (skipsResetAt), completed date cleared,
 *   driver kept, a JOB_RESET event; deleteJobAsAdmin soft-deletes any
 *   owner's job like the owner's delete.</li>
 *   <li>WHEN — only an active job that is not NEW; refused when an order of the job is on a newer job or deleted.</li>
 *   <li>THEN — the statuses written, the 400 message, and that nothing is written on a refusal.</li>
 * </ul>
 * ZOMBIES: Zero (job without stops) skipped — a job always has stops; Boundary has no limit here.
 */
@ExtendWith(MockitoExtension.class)
class JobServiceAdminTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 7, 10, 0);
    private static final Clock CLOCK = Clock.fixed(NOW.atZone(ZoneId.of("Asia/Bangkok")).toInstant(),
            ZoneId.of("Asia/Bangkok"));

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

    @BeforeEach
    void setUp() {
        jobService = new JobService(deliveryJobRepository, deliveryRoutingRepository, orderRepository,
                subdistrictRepository, subdistrictDistanceRepository, masterDataService, googleRoutesService, CLOCK,
                jobAccessService, jobDriverRequestService, deliveryJobEventRepository, userRepository, teamNotifier);
    }

    private static DeliveryJob job(String deliveryStatus) {
        return DeliveryJob.builder().jobId(1L).jobCode("JOB-1").deliveryStatus(deliveryStatus).status("A")
                .createdBy("owner1").assignedDriverId(3).completedDate(NOW.minusHours(1)).build();
    }

    private static DeliveryRouting stop(DeliveryJob job, long orderId, String routingStatus, String orderStatus,
                                        String reason) {
        Order order = Order.builder().orderId(orderId).orderCode("ORD-" + orderId).orderStatus(orderStatus)
                .deliveryDate(NOW.minusHours(2)).status("A").createdBy("owner1").build();
        return DeliveryRouting.builder().routeId(orderId).deliveryJob(job).order(order).sequenceNo((int) orderId)
                .routingStatus(routingStatus).statusReason(reason).build();
    }

    private void givenJob(DeliveryJob job, DeliveryRouting... stops) {
        when(deliveryJobRepository.findForUpdate(job.getJobId())).thenReturn(Optional.of(job));
        lenient().when(deliveryRoutingRepository.findOrdersMovedOnFrom(job.getJobId())).thenReturn(List.of());
        lenient().when(deliveryRoutingRepository.findByDeliveryJobJobIdOrderBySequenceNoAsc(job.getJobId()))
                .thenReturn(List.of(stops));
    }

    // ---- resetJobAsAdmin ----

    @Test
    void should_putEveryStopAndOrderBackToFirstStep_when_closedJobIsReset() {
        //GIVEN
        DeliveryJob job = job("COMPLETED");
        DeliveryRouting delivered = stop(job, 11L, "COMPLETED", "COMPLETED", null);
        DeliveryRouting skipped = stop(job, 12L, "SKIPPED", "FAILED", "CUSTOMER_ABSENT");
        DeliveryRouting cancelled = stop(job, 13L, "CANCELLED", "CANCELLED", "CHANGED_MIND");
        DeliveryRouting cancelRequested = stop(job, 14L, "SKIPPED", "IN_TRANSIT", "CANCEL_NOT_APPROVED");
        cancelRequested.setStatusNote("ลูกค้าโทรมายกเลิก");
        givenJob(job, delivered, skipped, cancelled, cancelRequested);

        //WHEN
        jobService.resetJobAsAdmin(1L, "god");

        //THEN — เหมือนงานที่เพิ่งสร้าง: ไม่เหลือเหตุผล / หมายเหตุ / วันที่ส่ง และเริ่มนับการข้ามใหม่
        assertThat(List.of(delivered, skipped, cancelled, cancelRequested))
                .extracting(DeliveryRouting::getRoutingStatus, DeliveryRouting::getStatusReason,
                        DeliveryRouting::getStatusNote, r -> r.getOrder().getOrderStatus(),
                        r -> r.getOrder().getDeliveryDate(), r -> r.getOrder().getSkipsResetAt())
                .containsOnly(tuple("NEW", null, null, "ASSIGNED", null, NOW));
        assertThat(job.getDeliveryStatus()).isEqualTo("NEW");
        assertThat(job.getCompletedDate()).isNull();
        assertThat(job.getAssignedDriverId()).isEqualTo(3);
        assertThat(job.getUpdatedBy()).isEqualTo("god");
        ArgumentCaptor<DeliveryJobEvent> event = ArgumentCaptor.forClass(DeliveryJobEvent.class);
        verify(deliveryJobEventRepository).save(event.capture());
        assertThat(event.getValue().getEventType()).isEqualTo(JobEventType.JOB_RESET);
        assertThat(event.getValue().getActorUsername()).isEqualTo("god");
    }

    @Test
    void should_resetInTransitOrders_when_startedJobIsReset() {
        //GIVEN
        DeliveryJob job = job("IN_PROGRESS");
        DeliveryRouting open = stop(job, 11L, "NEW", "IN_TRANSIT", null);
        givenJob(job, open);

        //WHEN
        jobService.resetJobAsAdmin(1L, "god");

        //THEN
        assertThat(open.getOrder().getOrderStatus()).isEqualTo("ASSIGNED");
        assertThat(job.getDeliveryStatus()).isEqualTo("NEW");
    }

    @Test
    void should_refuse_when_jobIsAlreadyNew() {
        //GIVEN
        when(deliveryJobRepository.findForUpdate(1L)).thenReturn(Optional.of(job("NEW")));

        //WHEN / THEN
        assertThatThrownBy(() -> jobService.resetJobAsAdmin(1L, "god"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ขั้นแรก");
        verify(deliveryJobRepository, never()).save(any());
    }

    @Test
    void should_refuse_when_anOrderWasPutOnANewerJob() {
        //GIVEN
        DeliveryJob job = job("COMPLETED");
        DeliveryRouting skipped = stop(job, 12L, "SKIPPED", "PENDING", "CUSTOMER_ABSENT");
        givenJob(job, skipped);
        when(deliveryRoutingRepository.findOrdersMovedOnFrom(1L)).thenReturn(List.of("ORD-12"));

        //WHEN / THEN
        assertThatThrownBy(() -> jobService.resetJobAsAdmin(1L, "god"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ORD-12");
        assertThat(skipped.getRoutingStatus()).isEqualTo("SKIPPED");
        verify(orderRepository, never()).save(any());
    }

    @Test
    void should_refuse_when_anOrderOfTheJobWasDeleted() {
        //GIVEN
        DeliveryJob job = job("COMPLETED");
        DeliveryRouting delivered = stop(job, 11L, "COMPLETED", "COMPLETED", null);
        delivered.getOrder().setStatus("I");
        givenJob(job, delivered);

        //WHEN / THEN
        assertThatThrownBy(() -> jobService.resetJobAsAdmin(1L, "god"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ORD-11");
        verify(orderRepository, never()).save(any());
    }

    @Test
    void should_answerNotFound_when_jobIsDeleted() {
        //GIVEN
        DeliveryJob job = job("IN_PROGRESS");
        job.setStatus("I");
        when(deliveryJobRepository.findForUpdate(1L)).thenReturn(Optional.of(job));

        //WHEN / THEN
        assertThatThrownBy(() -> jobService.resetJobAsAdmin(1L, "god"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Job not found");
    }

    // ---- deleteJobAsAdmin ----

    @Test
    void should_softDeleteAndFreeOpenOrders_when_adminDeletesSomeoneElsesJob() {
        //GIVEN
        DeliveryJob job = job("IN_PROGRESS");
        DeliveryRouting open = stop(job, 11L, "SKIPPED", "IN_TRANSIT", "CUSTOMER_ABSENT");
        DeliveryRouting delivered = stop(job, 12L, "COMPLETED", "COMPLETED", null);
        when(deliveryJobRepository.findById(1L)).thenReturn(Optional.of(job));
        when(deliveryRoutingRepository.findByDeliveryJobJobId(1L)).thenReturn(List.of(open, delivered));

        //WHEN
        jobService.deleteJobAsAdmin(1L, "god");

        //THEN
        assertThat(job.getStatus()).isEqualTo("I");
        assertThat(job.getUpdatedBy()).isEqualTo("god");
        assertThat(open.getOrder().getOrderStatus()).isEqualTo("NEW");
        assertThat(delivered.getOrder().getOrderStatus()).isEqualTo("COMPLETED");
        // ผู้ดูแลระบบลบ = ไม่แจ้งคนส่ง ทั้ง "ไม่ได้งานนี้แล้ว" และ "คำขอถูกยกเลิก" (owner delete ยังแจ้งเหมือนเดิม)
        verify(jobDriverRequestService).cancelPending(job, "god", false);
        verify(jobAccessService, never()).notifyUnassigned(any(), any());
    }

    @Test
    void should_stillNotifyTheDriver_when_ownerDeletesTheJob() {
        //GIVEN
        DeliveryJob job = job("NEW");
        when(deliveryJobRepository.findById(1L)).thenReturn(Optional.of(job));
        when(deliveryRoutingRepository.findByDeliveryJobJobId(1L)).thenReturn(List.of());

        //WHEN
        jobService.deleteJob(1L, "owner1");

        //THEN
        verify(jobAccessService).notifyUnassigned(job, 3);
        verify(jobDriverRequestService).cancelPending(job, "owner1");
    }

    // ---- disabled origin (admin.md US-ADM-06) ----

    @Test
    void should_refuseNewJob_when_originSubdistrictIsDisabled() {
        //GIVEN
        com.master.entity.Province p = com.master.entity.Province.builder().code("47").name("สกลนคร").build();
        com.master.entity.District d = com.master.entity.District.builder().code("4701").name("เมืองสกลนคร").province(p).build();
        com.master.entity.Subdistrict origin = com.master.entity.Subdistrict.builder().code("470101").name("ธาตุเชิงชุม")
                .district(d).status("I").build();
        when(subdistrictRepository.findByCode("470101")).thenReturn(Optional.of(origin));
        com.core.dto.JobRequest request = com.core.dto.JobRequest.builder().jobName("รอบเช้า")
                .originSubdistrictCode("470101").orderIds(List.of(11L)).build();

        //WHEN / THEN
        assertThatThrownBy(() -> jobService.createJob(request, "owner1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ตำบลธาตุเชิงชุม ถูกปิดใช้งานแล้ว");
        verify(deliveryJobRepository, never()).save(any());
    }
}
