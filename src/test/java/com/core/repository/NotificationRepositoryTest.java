package com.core.repository;

import com.core.constant.NotificationType;
import com.core.entity.DeliveryJob;
import com.core.entity.Notification;
import com.core.entity.Order;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the native / JPQL queries behind the job-due reminder against a real PostgreSQL:
 * {@code ON CONFLICT DO NOTHING}, the date-range filter and the retract rule cannot be proven with mocks.
 * Skipped when Docker is not available.
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class NotificationRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = Order.class)
    @EnableJpaRepositories(basePackageClasses = NotificationRepository.class)
    static class JpaSliceConfig {
    }

    private static final LocalDate TOMORROW = LocalDate.of(2026, 10, 22);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 21, 8, 5);

    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private DeliveryJobRepository deliveryJobRepository;
    @Autowired
    private TestEntityManager em;

    private DeliveryJob job(String code, String deliveryStatus, String status, LocalDateTime scheduledDate) {
        return em.persist(DeliveryJob.builder().jobCode(code).originSubdistrictCode("470101")
                .deliveryStatus(deliveryStatus).status(status).scheduledDate(scheduledDate).totalOrders(0).build());
    }

    private int insertReminder(DeliveryJob job, LocalDate targetDate) {
        return notificationRepository.insertIgnore("somchai", NotificationType.JOB_DUE_TOMORROW, "t", "m",
                job.getJobId(), targetDate, "JOB_DUE_TOMORROW:" + job.getJobId() + ":" + targetDate, NOW, null);
    }

    private List<String> remainingJobCodes(DeliveryJob... jobs) {
        em.clear();
        List<Long> remainingJobIds = notificationRepository.findAll().stream().map(Notification::getJobId).toList();
        return Arrays.stream(jobs).filter(j -> remainingJobIds.contains(j.getJobId()))
                .map(DeliveryJob::getJobCode).toList();
    }

    @Test
    void should_selectOnlyNewActiveJobsDueTomorrow_when_findingScheduledBetween() {
        //GIVEN — ปนทุกกรณีที่ไม่ควรได้ กับขอบช่วงเวลา (00:00 และ 23:59:59 ของพรุ่งนี้)
        job("DUE-START", "NEW", "A", TOMORROW.atStartOfDay());
        job("DUE-END", "NEW", "A", TOMORROW.atTime(23, 59, 59));
        job("STARTED", "IN_PROGRESS", "A", TOMORROW.atStartOfDay());
        job("COMPLETED", "COMPLETED", "A", TOMORROW.atStartOfDay());
        job("DELETED", "NEW", "I", TOMORROW.atStartOfDay());
        job("NO-DATE", "NEW", "A", null);
        job("TODAY", "NEW", "A", TOMORROW.minusDays(1).atStartOfDay());
        job("DAY-AFTER", "NEW", "A", TOMORROW.plusDays(1).atStartOfDay());
        em.flush();

        //WHEN
        List<DeliveryJob> due = deliveryJobRepository.findScheduledBetween("NEW",
                TOMORROW.atStartOfDay(), TOMORROW.plusDays(1).atStartOfDay());

        //THEN
        assertThat(due).extracting(DeliveryJob::getJobCode).containsExactly("DUE-START", "DUE-END");
    }

    @Test
    void should_ignoreConflict_when_insertingSameDedupeKeyTwice() {
        //GIVEN
        DeliveryJob job = job("DUE", "NEW", "A", TOMORROW.atStartOfDay());
        em.flush();

        //WHEN
        int first = insertReminder(job, TOMORROW);
        int second = insertReminder(job, TOMORROW);

        //THEN
        assertThat(first).isEqualTo(1);
        assertThat(second).isZero();
        assertThat(notificationRepository.count()).isEqualTo(1);
    }

    @Test
    void should_retractOnlyFormerDriversUnreadReminder_when_driverChanged() {
        //GIVEN — job of somchai now driven by driver2 (id 4); driver1 (id 3) was replaced
        em.getEntityManager().createNativeQuery(
                "create table if not exists users (user_id integer primary key, username varchar(50))").executeUpdate();
        em.getEntityManager().createNativeQuery(
                "insert into users (user_id, username) values (3, 'driver1'), (4, 'driver2')").executeUpdate();
        DeliveryJob job = em.persist(DeliveryJob.builder().jobCode("J").deliveryStatus("NEW").status("A")
                .createdBy("somchai").assignedDriverId(4).scheduledDate(TOMORROW.atStartOfDay()).totalOrders(0).build());
        em.flush();
        String key = "JOB_DUE_TOMORROW:" + job.getJobId() + ":" + TOMORROW;
        for (String recipient : List.of("somchai", "driver1", "driver2")) {
            notificationRepository.insertIgnore(recipient, NotificationType.JOB_DUE_TOMORROW, "t", "m",
                    job.getJobId(), TOMORROW, key, NOW, null);
        }

        //WHEN
        int retracted = notificationRepository.deleteRemindersOfFormerDrivers(NotificationType.JOB_DUE_TOMORROW);

        //THEN
        assertThat(retracted).isEqualTo(1);
        em.clear();
        assertThat(notificationRepository.findAll()).extracting(Notification::getRecipientUsername)
                .containsExactlyInAnyOrder("somchai", "driver2");
    }

    @Test
    void should_deleteOnlyUnreadStaleReminders_when_retracting() {
        //GIVEN
        DeliveryJob stillDue = job("STILL-DUE", "NEW", "A", TOMORROW.atTime(9, 0));
        DeliveryJob rescheduled = job("RESCHEDULED", "NEW", "A", TOMORROW.plusDays(2).atStartOfDay());
        DeliveryJob started = job("STARTED", "IN_PROGRESS", "A", TOMORROW.atStartOfDay());
        DeliveryJob deleted = job("DELETED", "NEW", "I", TOMORROW.atStartOfDay());
        DeliveryJob readButStale = job("READ-STALE", "IN_PROGRESS", "A", TOMORROW.atStartOfDay());
        em.flush();
        for (DeliveryJob job : List.of(stillDue, rescheduled, started, deleted, readButStale)) {
            insertReminder(job, TOMORROW);
        }
        notificationRepository.findAll().stream()
                .filter(n -> n.getJobId().equals(readButStale.getJobId()))
                .forEach(n -> n.setReadAt(NOW));
        em.flush();

        //WHEN
        int retracted = notificationRepository.deleteStaleJobReminders(NotificationType.JOB_DUE_TOMORROW);

        //THEN — ที่อ่านแล้วเก็บไว้เป็นประวัติ
        assertThat(retracted).isEqualTo(3);
        assertThat(remainingJobCodes(stillDue, rescheduled, started, deleted, readButStale))
                .containsExactlyInAnyOrder("STILL-DUE", "READ-STALE");
    }
}
