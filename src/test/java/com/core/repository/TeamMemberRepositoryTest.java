package com.core.repository;

import com.core.constant.TeamMemberStatus;
import com.core.entity.Customer;
import com.core.entity.DeliveryJob;
import com.core.entity.DeliveryRouting;
import com.core.entity.Order;
import com.core.entity.OrderAddress;
import com.core.entity.TeamMember;
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
 * Runs the team queries against a real PostgreSQL: the one-open-row-per-pair rule is a partial unique index
 * used by {@code ON CONFLICT}, which mocks cannot prove. Skipped when Docker is not available.
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class TeamMemberRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = Order.class)
    @EnableJpaRepositories(basePackageClasses = TeamMemberRepository.class)
    static class JpaSliceConfig {
    }

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 1, 10, 0);

    @Autowired
    private TeamMemberRepository teamMemberRepository;
    @Autowired
    private DeliveryJobRepository deliveryJobRepository;
    @Autowired
    private TestEntityManager em;

    @BeforeEach
    void createPartialUniqueIndex() {
        // ddl-auto cannot express a partial index; same statement as db/alter-add-team.sql
        em.getEntityManager().createNativeQuery("create unique index if not exists uq_team_members_open "
                + "on team_members(owner_user_id, member_user_id) where member_status in ('INVITED', 'ACTIVE')")
                .executeUpdate();
    }

    /** The users table belongs to the authen module (not in this slice); the native queries only need these two columns. */
    private void users(String... idAndUsername) {
        em.getEntityManager().createNativeQuery(
                "create table if not exists users (user_id integer primary key, username varchar(50))").executeUpdate();
        for (String pair : idAndUsername) {
            String[] parts = pair.split(":");
            em.getEntityManager().createNativeQuery("insert into users (user_id, username) values (?1, ?2)")
                    .setParameter(1, Integer.parseInt(parts[0])).setParameter(2, parts[1]).executeUpdate();
        }
    }

    private void answer(int ownerUserId, int memberUserId, String newStatus) {
        TeamMember row = teamMemberRepository.findFirstByOwnerUserIdAndMemberUserIdAndMemberStatus(
                ownerUserId, memberUserId, TeamMemberStatus.INVITED).orElseThrow();
        row.setMemberStatus(newStatus);
        em.flush();
    }

    private DeliveryJob job(String code, String owner, Integer driver, String deliveryStatus, String status) {
        return em.persist(DeliveryJob.builder().jobCode(code).createdBy(owner).assignedDriverId(driver)
                .deliveryStatus(deliveryStatus).status(status).build());
    }

    @Test
    void should_createOneRow_when_samePairInvitedTwice() {
        //GIVEN
        teamMemberRepository.insertInvitation(1, 3, NOW, "admin");
        //WHEN
        int second = teamMemberRepository.insertInvitation(1, 3, NOW, "admin");
        //THEN
        assertThat(second).isZero();
        assertThat(teamMemberRepository.findAll()).hasSize(1);
    }

    @Test
    void should_allowNewInvitation_when_previousRowEnded() {
        //GIVEN
        teamMemberRepository.insertInvitation(1, 3, NOW, "admin");
        answer(1, 3, TeamMemberStatus.REMOVED);
        //WHEN
        int again = teamMemberRepository.insertInvitation(1, 3, NOW.plusDays(1), "admin");
        //THEN
        assertThat(again).isEqualTo(1);
        assertThat(teamMemberRepository.findAll()).extracting(TeamMember::getMemberStatus)
                .containsExactlyInAnyOrder(TeamMemberStatus.REMOVED, TeamMemberStatus.INVITED);
    }

    @Test
    void should_letOneDriverJoinManyTeams_when_invitedByDifferentOwners() {
        //GIVEN
        teamMemberRepository.insertInvitation(1, 3, NOW, "admin");
        //WHEN
        int otherOwner = teamMemberRepository.insertInvitation(2, 3, NOW, "owner2");
        //THEN
        assertThat(otherOwner).isEqualTo(1);
    }

    @Test
    void should_returnAssignedJobsOfActiveTeamsOnlyInAnyStatus_when_listingDriverJobs() {
        //GIVEN — driver 3 is ACTIVE with admin (1), LEFT owner2 (2)
        users("1:admin", "2:owner2", "3:driver1");
        teamMemberRepository.insertInvitation(1, 3, NOW, "admin");
        answer(1, 3, TeamMemberStatus.ACTIVE);
        teamMemberRepository.insertInvitation(2, 3, NOW, "owner2");
        answer(2, 3, TeamMemberStatus.LEFT);
        job("J1", "admin", 3, "NEW", "A");
        job("J2", "admin", 3, "COMPLETED", "A");   // closed — still shown (design §10 Q1)
        job("J3", "admin", 3, "NEW", "I");         // deleted
        job("J4", "admin", 4, "NEW", "A");         // another driver
        job("J5", "owner2", 3, "NEW", "A");        // team the driver left
        em.flush();
        //WHEN
        List<DeliveryJob> jobs = deliveryJobRepository.findAssignedToDriver(3);
        //THEN
        assertThat(jobs).extracting(DeliveryJob::getJobCode).containsExactlyInAnyOrder("J1", "J2");
    }

    @Test
    void should_answerActiveOnlyForActiveRow_when_checkingMembershipByOwnerUsername() {
        //GIVEN
        users("1:admin", "2:owner2");
        teamMemberRepository.insertInvitation(1, 3, NOW, "admin");
        answer(1, 3, TeamMemberStatus.ACTIVE);
        teamMemberRepository.insertInvitation(2, 3, NOW, "owner2");   // still INVITED
        em.flush();
        //WHEN
        boolean admin = teamMemberRepository.existsActiveMembership("admin", 3);
        boolean owner2 = teamMemberRepository.existsActiveMembership("owner2", 3);
        //THEN
        assertThat(admin).isTrue();
        assertThat(owner2).isFalse();
    }

    @Test
    void should_countOnlyActiveUnfinishedJobsOfThatPair_when_countingOpenAssigned() {
        //GIVEN
        job("J1", "admin", 3, "NEW", "A");
        job("J2", "admin", 3, "IN_PROGRESS", "A");
        job("J3", "admin", 3, "COMPLETED", "A");   // closed
        job("J4", "admin", 3, "NEW", "I");         // deleted
        job("J5", "owner2", 3, "NEW", "A");        // another team
        job("J6", "admin", 4, "NEW", "A");         // another driver
        job("J7", "admin", null, "NEW", "A");      // not assigned
        em.flush();
        //WHEN
        long pair = deliveryJobRepository.countOpenAssigned("admin", 3);
        List<DeliveryJobRepository.OpenAssignedJob> byDriver = deliveryJobRepository.findOpenAssignedByOwner("admin");
        List<DeliveryJobRepository.OpenJobCountByOwner> byOwner = deliveryJobRepository.countOpenAssignedByOwner(3);
        //THEN
        assertThat(pair).isEqualTo(2);
        assertThat(byDriver).extracting(DeliveryJobRepository.OpenAssignedJob::getDriverUserId,
                        DeliveryJobRepository.OpenAssignedJob::getJobCode)
                .containsExactlyInAnyOrder(tuple(3, "J1"), tuple(3, "J2"),
                        tuple(4, "J6"));
        assertThat(byOwner).extracting(DeliveryJobRepository.OpenJobCountByOwner::getOwnerUsername,
                        DeliveryJobRepository.OpenJobCountByOwner::getOpenJobCount)
                .containsExactlyInAnyOrder(tuple("admin", 2L),
                        tuple("owner2", 1L));
    }

    private void stop(DeliveryJob job, String orderCode, String routingStatus) {
        Customer customer = em.persist(Customer.builder().name("c-" + orderCode).build());
        OrderAddress address = em.persist(OrderAddress.builder().addressLine("a-" + orderCode)
                .provinceCode("47").districtCode("4701").subdistrictCode("470101").build());
        Order order = em.persist(Order.builder().orderCode(orderCode).platformId(1L)
                .customer(customer).address(address).orderStatus("IN_TRANSIT").status("A").build());
        em.persist(DeliveryRouting.builder().deliveryJob(job).order(order).sequenceNo(1)
                .routingStatus(routingStatus).build());
    }

    @Test
    void should_countAllStopsAndDeliveredOnes_when_listingOpenAssignedJobs() {
        //GIVEN — J1: 1 delivered, 1 waiting, 1 skipped, 1 cancelled · J2: no stops yet
        DeliveryJob j1 = job("J1", "admin", 3, "IN_PROGRESS", "A");
        stop(j1, "O1", "COMPLETED");
        stop(j1, "O2", "NEW");
        stop(j1, "O3", "SKIPPED");
        stop(j1, "O4", "CANCELLED");
        job("J2", "admin", 3, "NEW", "A");
        em.flush();
        //WHEN
        List<DeliveryJobRepository.OpenAssignedJob> result = deliveryJobRepository.findOpenAssignedByOwner("admin");
        //THEN
        assertThat(result).extracting(DeliveryJobRepository.OpenAssignedJob::getJobCode,
                        DeliveryJobRepository.OpenAssignedJob::getDeliveryStatus,
                        DeliveryJobRepository.OpenAssignedJob::getStopCount,
                        DeliveryJobRepository.OpenAssignedJob::getCompletedStopCount)
                .containsExactlyInAnyOrder(tuple("J1", "IN_PROGRESS", 4L, 1L),
                        tuple("J2", "NEW", 0L, 0L));
    }

    @Test
    void should_returnOnlyDriversWithStartedJobOfActiveTeam_when_findingDelivering() {
        //GIVEN — 3: started job of admin (ACTIVE) · 4: started job of owner2 but LEFT · 5: only a NEW job ·
        // 6: started but deleted job
        users("1:admin", "2:owner2");
        for (int driver : new int[]{3, 5, 6}) {
            teamMemberRepository.insertInvitation(1, driver, NOW, "admin");
            answer(1, driver, TeamMemberStatus.ACTIVE);
        }
        teamMemberRepository.insertInvitation(2, 4, NOW, "owner2");
        answer(2, 4, TeamMemberStatus.LEFT);
        job("J1", "admin", 3, "IN_PROGRESS", "A");
        job("J2", "owner2", 4, "IN_PROGRESS", "A");
        job("J3", "admin", 5, "NEW", "A");
        job("J4", "admin", 6, "IN_PROGRESS", "I");
        job("J5", "admin", 3, "IN_PROGRESS", "A");   // second started job — driver 3 listed once
        em.flush();
        //WHEN
        List<Integer> delivering = deliveryJobRepository.findDeliveringDrivers(List.of(3, 4, 5, 6, 7));
        //THEN
        assertThat(delivering).containsExactly(3);
    }
}
