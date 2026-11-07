package com.core.service.notification.reminder;

import com.core.constant.JobStatus;
import com.core.constant.NotificationType;
import com.core.entity.Customer;
import com.core.entity.DeliveryJob;
import com.core.entity.DeliveryRouting;
import com.core.entity.Notification;
import com.core.entity.Order;
import com.core.repository.DeliveryJobRepository;
import com.core.repository.DeliveryRoutingRepository;
import com.core.repository.JobDriverRequestRepository;
import com.authen.entity.User;
import com.authen.repository.UserRepository;
import com.core.repository.NotificationRepository;
import com.core.repository.TeamMemberRepository;
import com.core.service.notification.NotificationPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers {@link JobDueReminderService#run}: which jobs get a "due tomorrow" notification, for whom, and that
 * stale reminders are retracted before new ones are created.
 */
@ExtendWith(MockitoExtension.class)
class JobDueReminderServiceTest {

    private static final ZoneId BANGKOK = ZoneId.of("Asia/Bangkok");
    // 21 ต.ค. 2026 08:05 เวลาไทย → "พรุ่งนี้" = 22 ต.ค.
    private static final LocalDate TOMORROW = LocalDate.of(2026, 10, 22);

    @Mock
    private DeliveryJobRepository deliveryJobRepository;
    @Mock
    private DeliveryRoutingRepository deliveryRoutingRepository;
    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private NotificationPublisher notificationPublisher;
    @Mock
    private UserRepository userRepository;
    @Mock
    private TeamMemberRepository teamMemberRepository;
    @Mock
    private JobDriverRequestRepository jobDriverRequestRepository;

    private JobDueReminderService service(LocalDateTime bangkokNow) {
        Clock clock = Clock.fixed(bangkokNow.atZone(BANGKOK).toInstant(), BANGKOK);
        return new JobDueReminderService(deliveryJobRepository, deliveryRoutingRepository, notificationRepository,
                notificationPublisher, userRepository, teamMemberRepository, jobDriverRequestRepository, clock);
    }

    private JobDueReminderService service() {
        return service(LocalDateTime.of(2026, 10, 21, 8, 5));
    }

    private DeliveryJob job(long id, String createdBy) {
        return DeliveryJob.builder().jobId(id).jobCode("JOB-" + id).jobName("งาน " + id)
                .deliveryStatus(JobStatus.NEW).createdBy(createdBy).build();
    }

    private DeliveryRouting stop(String customerName, String routingStatus) {
        Order order = Order.builder().customer(Customer.builder().name(customerName).build()).build();
        return DeliveryRouting.builder().order(order).routingStatus(routingStatus).build();
    }

    private void givenJobsDueTomorrow(DeliveryJob... jobs) {
        when(deliveryJobRepository.findScheduledBetween(JobStatus.NEW,
                TOMORROW.atStartOfDay(), TOMORROW.plusDays(1).atStartOfDay())).thenReturn(List.of(jobs));
    }

    @Test
    void should_createNothing_when_noJobScheduledTomorrow() {
        //GIVEN
        givenJobsDueTomorrow();

        //WHEN
        JobDueReminderService.Result result = service().run();

        //THEN
        assertThat(result.created()).isZero();
        verify(notificationPublisher, never()).publish(any());
    }

    @Test
    void should_createOneNotificationForOwner_when_newJobScheduledTomorrow() {
        //GIVEN
        givenJobsDueTomorrow(job(7L, "somchai"));
        when(deliveryRoutingRepository.findByDeliveryJobJobIdOrderBySequenceNoAsc(7L))
                .thenReturn(List.of(stop("สมชาย", "NEW")));
        when(notificationPublisher.publish(any())).thenReturn(true);

        //WHEN
        JobDueReminderService.Result result = service().run();

        //THEN
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationPublisher).publish(captor.capture());
        Notification published = captor.getValue();
        assertThat(result.created()).isEqualTo(1);
        assertThat(published.getRecipientUsername()).isEqualTo("somchai");
        assertThat(published.getNotificationType()).isEqualTo(NotificationType.JOB_DUE_TOMORROW);
        assertThat(published.getJobId()).isEqualTo(7L);
        assertThat(published.getTargetDate()).isEqualTo(TOMORROW);
        assertThat(published.getDedupeKey()).isEqualTo("JOB_DUE_TOMORROW:7:2026-10-22");
        assertThat(published.getMessage()).contains("“งาน 7” · 1 จุด: สมชาย");
    }

    @Test
    void should_tellOwnerDriverHasNotAnswered_when_jobRequestIsStillPending() {
        //GIVEN — assignConfirm G8: the driver was asked but has not answered (no assigned driver yet)
        givenJobsDueTomorrow(job(7L, "somchai"));
        when(deliveryRoutingRepository.findByDeliveryJobJobIdOrderBySequenceNoAsc(7L))
                .thenReturn(List.of(stop("สมชาย", "NEW")));
        when(jobDriverRequestRepository.existsByJobIdAndRequestStatus(7L, "PENDING")).thenReturn(true);
        when(notificationPublisher.publish(any())).thenReturn(true);

        //WHEN
        service().run();

        //THEN
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationPublisher).publish(captor.capture());
        assertThat(captor.getValue().getRecipientUsername()).isEqualTo("somchai");
        assertThat(captor.getValue().getMessage()).endsWith(" · ยังรอคนส่งตอบรับ");
    }

    @Test
    void should_createOneNotificationPerJob_when_manyJobsScheduledTomorrow() {
        //GIVEN
        givenJobsDueTomorrow(job(1L, "a"), job(2L, "b"), job(3L, "a"));
        when(deliveryRoutingRepository.findByDeliveryJobJobIdOrderBySequenceNoAsc(anyLong())).thenReturn(List.of());
        when(notificationPublisher.publish(any())).thenReturn(true);

        //WHEN
        JobDueReminderService.Result result = service().run();

        //THEN
        assertThat(result.created()).isEqualTo(3);
        verify(notificationPublisher, times(3)).publish(any());
    }

    @Test
    void should_treatTomorrowByBangkokDate_when_clockIsJustAfterMidnight() {
        //GIVEN — 22 ต.ค. 00:01 เวลาไทย (= 21 ต.ค. 17:01 UTC) → พรุ่งนี้ต้องเป็น 23 ต.ค. ไม่ใช่ 22
        LocalDate expectedTomorrow = LocalDate.of(2026, 10, 23);
        when(deliveryJobRepository.findScheduledBetween(any(), any(), any())).thenReturn(List.of());

        //WHEN
        service(LocalDateTime.of(2026, 10, 22, 0, 1)).run();

        //THEN
        verify(deliveryJobRepository).findScheduledBetween(JobStatus.NEW,
                expectedTomorrow.atStartOfDay(), expectedTomorrow.plusDays(1).atStartOfDay());
    }

    @Test
    void should_alsoRemindAssignedDriver_when_driverIsStillActiveMember() {
        //GIVEN
        DeliveryJob job = job(7L, "somchai");
        job.setAssignedDriverId(3);
        givenJobsDueTomorrow(job);
        when(deliveryRoutingRepository.findByDeliveryJobJobIdOrderBySequenceNoAsc(7L))
                .thenReturn(List.of(stop("สมชาย", "NEW")));
        when(teamMemberRepository.existsActiveMembership("somchai", 3)).thenReturn(true);
        User driver = new User();
        driver.setUsername("driver1");
        when(userRepository.findById(3)).thenReturn(Optional.of(driver));
        when(notificationPublisher.publish(any())).thenReturn(true);

        //WHEN
        JobDueReminderService.Result result = service().run();

        //THEN — same dedupe key, one row per recipient
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationPublisher, times(2)).publish(captor.capture());
        assertThat(captor.getAllValues()).extracting(Notification::getRecipientUsername)
                .containsExactly("somchai", "driver1");
        assertThat(captor.getAllValues()).extracting(Notification::getDedupeKey).containsOnly("JOB_DUE_TOMORROW:7:2026-10-22");
        assertThat(result.created()).isEqualTo(2);
    }

    @Test
    void should_remindOwnerOnly_when_assignedDriverLeftTheTeam() {
        //GIVEN
        DeliveryJob job = job(7L, "somchai");
        job.setAssignedDriverId(3);
        givenJobsDueTomorrow(job);
        when(deliveryRoutingRepository.findByDeliveryJobJobIdOrderBySequenceNoAsc(7L))
                .thenReturn(List.of(stop("สมชาย", "NEW")));
        when(teamMemberRepository.existsActiveMembership("somchai", 3)).thenReturn(false);
        when(notificationPublisher.publish(any())).thenReturn(true);

        //WHEN
        service().run();

        //THEN
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationPublisher).publish(captor.capture());
        assertThat(captor.getValue().getRecipientUsername()).isEqualTo("somchai");
        verify(userRepository, never()).findById(any());
    }

    @Test
    void should_skipJob_when_createdByIsBlank() {
        //GIVEN — งานไม่มีเจ้าของ ไม่รู้จะแจ้งใคร
        givenJobsDueTomorrow(job(1L, null), job(2L, " "));

        //WHEN
        JobDueReminderService.Result result = service().run();

        //THEN
        assertThat(result.created()).isZero();
        verify(notificationPublisher, never()).publish(any());
    }

    @Test
    void should_excludeCancelledStops_when_buildingMessage() {
        //GIVEN
        givenJobsDueTomorrow(job(7L, "somchai"));
        when(deliveryRoutingRepository.findByDeliveryJobJobIdOrderBySequenceNoAsc(7L))
                .thenReturn(List.of(stop("ยกเลิกแล้ว", "CANCELLED"), stop("สมหญิง", "NEW")));
        when(notificationPublisher.publish(any())).thenReturn(true);

        //WHEN
        service().run();

        //THEN
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationPublisher).publish(captor.capture());
        assertThat(captor.getValue().getMessage()).contains("1 จุด: สมหญิง").doesNotContain("ยกเลิกแล้ว");
    }

    @Test
    void should_notCountDuplicate_when_runTwiceForSameJobAndDate() {
        //GIVEN — มีแจ้งเตือนของงาน + วันนี้อยู่แล้ว: publisher ตอบ false (ชน dedupe key)
        givenJobsDueTomorrow(job(7L, "somchai"));
        when(deliveryRoutingRepository.findByDeliveryJobJobIdOrderBySequenceNoAsc(7L)).thenReturn(List.of());
        when(notificationPublisher.publish(any())).thenReturn(false);

        //WHEN
        JobDueReminderService.Result result = service().run();

        //THEN
        assertThat(result.created()).isZero();
    }

    @Test
    void should_retractStaleRemindersBeforeCreating_when_running() {
        //GIVEN
        when(notificationRepository.deleteStaleJobReminders(NotificationType.JOB_DUE_TOMORROW)).thenReturn(2);
        givenJobsDueTomorrow(job(7L, "somchai"));
        lenient().when(deliveryRoutingRepository.findByDeliveryJobJobIdOrderBySequenceNoAsc(7L)).thenReturn(List.of());
        when(notificationPublisher.publish(any())).thenReturn(true);

        //WHEN
        JobDueReminderService.Result result = service().run();

        //THEN
        assertThat(result.retracted()).isEqualTo(2);
        InOrder order = inOrder(notificationRepository, notificationPublisher);
        order.verify(notificationRepository).deleteStaleJobReminders(eq(NotificationType.JOB_DUE_TOMORROW));
        order.verify(notificationPublisher).publish(any());
    }
}
