package com.core.service.notification.reminder;

import com.authen.entity.User;
import com.authen.repository.UserRepository;
import com.core.entity.DeliveryJob;
import com.core.entity.Notification;
import com.core.repository.DeliveryJobRepository;
import com.core.repository.TeamMemberRepository;
import com.core.service.notification.NotificationPublisher;
import com.core.service.team.JobRequestPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** "งานวันนี้ยังไม่เริ่ม" — today's NEW jobs with a driver, from 10:00 (driver-risk ข้อ 16). */
@ExtendWith(MockitoExtension.class)
class JobLateStartServiceTest {

    private static final ZoneId BANGKOK = ZoneId.of("Asia/Bangkok");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 7);

    @Mock
    private DeliveryJobRepository deliveryJobRepository;
    @Mock
    private NotificationPublisher notificationPublisher;
    @Mock
    private UserRepository userRepository;
    @Mock
    private TeamMemberRepository teamMemberRepository;

    private JobLateStartService serviceAt(int hour, int minute) {
        Clock clock = Clock.fixed(TODAY.atTime(hour, minute).atZone(BANGKOK).toInstant(), BANGKOK);
        return new JobLateStartService(deliveryJobRepository, notificationPublisher, userRepository,
                teamMemberRepository, new JobRequestPolicy(24, 8, 2, 10), clock);
    }

    private static DeliveryJob job(long id, Integer driverId) {
        return DeliveryJob.builder().jobId(id).jobCode("JOB-" + id).jobName("รอบเช้า").status("A")
                .deliveryStatus("NEW").createdBy("admin").assignedDriverId(driverId)
                .scheduledDate(TODAY.atStartOfDay()).build();
    }

    private static User driver() {
        User user = new User();
        user.setUserId(3);
        user.setUsername("driver1");
        return user;
    }

    @Test
    void should_doNothing_when_runBeforeLateHour() {
        //WHEN — boundary 09:59
        int created = serviceAt(9, 59).run();
        //THEN
        assertThat(created).isZero();
        verifyNoInteractions(deliveryJobRepository, notificationPublisher);
    }

    @Test
    void should_tellOwnerAndActiveDriverOncePerDay_when_acceptedJobHasNotStarted() {
        //GIVEN — boundary 10:00 · งานที่ไม่มีคนส่งข้าม
        when(deliveryJobRepository.findScheduledBetween("NEW", TODAY.atStartOfDay(), TODAY.plusDays(1).atStartOfDay()))
                .thenReturn(List.of(job(1L, 3), job(2L, null)));
        when(userRepository.findById(3)).thenReturn(Optional.of(driver()));
        when(teamMemberRepository.existsActiveMembership("admin", 3)).thenReturn(true);
        when(notificationPublisher.publish(any())).thenReturn(true);
        //WHEN
        int created = serviceAt(10, 0).run();
        //THEN
        assertThat(created).isEqualTo(2);
        ArgumentCaptor<Notification> sent = ArgumentCaptor.forClass(Notification.class);
        verify(notificationPublisher, times(2)).publish(sent.capture());
        assertThat(sent.getAllValues()).extracting(Notification::getRecipientUsername).containsExactly("admin", "driver1");
        assertThat(sent.getAllValues()).extracting(Notification::getDedupeKey)
                .containsOnly("JOB_NOT_STARTED:1:2026-11-07");
        assertThat(sent.getAllValues()).extracting(Notification::getJobId).containsOnly(1L);
    }

    @Test
    void should_tellOnlyOwner_when_driverLeftTheTeam() {
        //GIVEN
        when(deliveryJobRepository.findScheduledBetween(any(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(job(1L, 3)));
        when(userRepository.findById(3)).thenReturn(Optional.of(driver()));
        when(teamMemberRepository.existsActiveMembership("admin", 3)).thenReturn(false);
        when(notificationPublisher.publish(any())).thenReturn(true);
        //WHEN
        int created = serviceAt(15, 0).run();
        //THEN
        assertThat(created).isEqualTo(1);
        verify(notificationPublisher, times(1)).publish(any());
    }
}
