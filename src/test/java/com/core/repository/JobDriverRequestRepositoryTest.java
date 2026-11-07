package com.core.repository;

import com.core.constant.JobDriverRequestStatus;
import com.core.entity.JobDriverRequest;
import com.core.entity.Order;
import org.junit.jupiter.api.BeforeEach;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Runs the job request queries against a real PostgreSQL: one PENDING request per job is a partial unique index used
 * by {@code ON CONFLICT}, and "newest request per job" is a {@code DISTINCT ON} — neither can be proved with mocks.
 * Skipped when Docker is not available.
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class JobDriverRequestRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = Order.class)
    @EnableJpaRepositories(basePackageClasses = JobDriverRequestRepository.class)
    static class JpaSliceConfig {
    }

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 2, 10, 0);

    @Autowired
    private JobDriverRequestRepository repository;
    @Autowired
    private TestEntityManager em;

    @BeforeEach
    void createPartialUniqueIndex() {
        // ddl-auto cannot express a partial index; same statement as db/alter-add-job-driver-requests.sql
        em.getEntityManager().createNativeQuery("create unique index if not exists uq_job_driver_requests_pending "
                + "on job_driver_requests (job_id) where request_status = 'PENDING'").executeUpdate();
    }

    private void answer(Long jobId, String newStatus) {
        JobDriverRequest pending = repository.findFirstByJobIdAndRequestStatus(jobId, JobDriverRequestStatus.PENDING)
                .orElseThrow();
        pending.setRequestStatus(newStatus);
        em.flush();
    }

    @Test
    void should_keepOnePendingRequest_when_sameJobIsAskedTwice() {
        //GIVEN
        repository.insertPending(10L, 1, 3, NOW, "admin");
        //WHEN — another driver for the same job, e.g. two clicks racing
        int second = repository.insertPending(10L, 1, 4, NOW, "admin");
        //THEN
        assertThat(second).isZero();
        assertThat(repository.findAll()).extracting(JobDriverRequest::getDriverUserId).containsExactly(3);
    }

    @Test
    void should_allowNewRequest_when_previousOneWasAnswered() {
        //GIVEN
        repository.insertPending(10L, 1, 3, NOW, "admin");
        answer(10L, JobDriverRequestStatus.DECLINED);
        //WHEN
        int again = repository.insertPending(10L, 1, 3, NOW.plusMinutes(5), "admin");
        //THEN
        assertThat(again).isEqualTo(1);
        assertThat(repository.findAll()).extracting(JobDriverRequest::getRequestStatus)
                .containsExactlyInAnyOrder(JobDriverRequestStatus.DECLINED, JobDriverRequestStatus.PENDING);
    }

    @Test
    void should_returnNewestRequestOfEachJob_when_jobsHaveHistory() {
        //GIVEN — job 10: declined then asked again (pending) · job 11: declined only · job 12: none
        repository.insertPending(10L, 1, 3, NOW, "admin");
        answer(10L, JobDriverRequestStatus.DECLINED);
        repository.insertPending(10L, 1, 4, NOW.plusMinutes(1), "admin");
        repository.insertPending(11L, 1, 3, NOW, "admin");
        answer(11L, JobDriverRequestStatus.DECLINED);
        em.clear();
        //WHEN
        List<JobDriverRequest> latest = repository.findLatestByJobIds(List.of(10L, 11L, 12L));
        //THEN
        assertThat(latest).extracting(JobDriverRequest::getJobId, JobDriverRequest::getDriverUserId,
                        JobDriverRequest::getRequestStatus)
                .containsExactlyInAnyOrder(tuple(10L, 4, JobDriverRequestStatus.PENDING),
                        tuple(11L, 3, JobDriverRequestStatus.DECLINED));
    }

    @Test
    void should_listEachDeclinerOnce_when_driverDeclinedSameJobTwice() {
        //GIVEN
        repository.insertPending(10L, 1, 3, NOW, "admin");
        answer(10L, JobDriverRequestStatus.DECLINED);
        repository.insertPending(10L, 1, 3, NOW.plusMinutes(1), "admin");
        answer(10L, JobDriverRequestStatus.DECLINED);
        //WHEN
        List<JobDriverRequestRepository.JobDriver> declined = repository.findDeclinedDrivers(List.of(10L));
        //THEN
        assertThat(declined).extracting(JobDriverRequestRepository.JobDriver::getJobId,
                JobDriverRequestRepository.JobDriver::getDriverUserId).containsExactly(tuple(10L, 3));
    }

    @Test
    void should_cancelOnlyPendingRequestsOfThatPair_when_driverLeavesTeam() {
        //GIVEN — driver 3: pending from owner 1 and owner 2, accepted from owner 1 · driver 4: pending from owner 1
        repository.insertPending(10L, 1, 3, NOW, "admin");
        repository.insertPending(11L, 1, 3, NOW, "admin");
        answer(11L, JobDriverRequestStatus.ACCEPTED);
        repository.insertPending(12L, 2, 3, NOW, "owner2");
        repository.insertPending(13L, 1, 4, NOW, "admin");
        //WHEN
        int cancelled = repository.cancelPendingOfPair(1, 3, NOW.plusHours(1), "driver1");
        em.clear();
        //THEN
        assertThat(cancelled).isEqualTo(1);
        assertThat(repository.findAll()).extracting(JobDriverRequest::getJobId, JobDriverRequest::getRequestStatus)
                .containsExactlyInAnyOrder(
                        tuple(10L, JobDriverRequestStatus.CANCELLED),
                        tuple(11L, JobDriverRequestStatus.ACCEPTED),
                        tuple(12L, JobDriverRequestStatus.PENDING),
                        tuple(13L, JobDriverRequestStatus.PENDING));
        assertThat(repository.countPendingByDriver(1)).extracting(
                JobDriverRequestRepository.PendingCountByDriver::getDriverUserId,
                JobDriverRequestRepository.PendingCountByDriver::getPendingCount).containsExactly(tuple(4, 1L));
    }
}
