package com.core.repository;

import com.core.entity.Customer;
import com.core.entity.DeliveryJob;
import com.core.entity.DeliveryRouting;
import com.core.entity.Order;
import com.core.entity.OrderAddress;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs {@link DeliveryRoutingRepository#countSkipsByOrder} against a real PostgreSQL: the JPQL filters on
 * the joined job (closed / current / soft-deleted) and groups by order, which a Mockito mock cannot prove.
 * Skipped when Docker is not available.
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class DeliveryRoutingRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = Order.class)
    @EnableJpaRepositories(basePackageClasses = DeliveryRoutingRepository.class)
    static class JpaSliceConfig {
    }

    @Autowired
    private DeliveryRoutingRepository deliveryRoutingRepository;
    @Autowired
    private TestEntityManager em;

    private Order order(String code) {
        Customer customer = em.persist(Customer.builder().name("c-" + code).build());
        OrderAddress address = em.persist(OrderAddress.builder().addressLine("a-" + code)
                .provinceCode("47").districtCode("4701").subdistrictCode("470101").build());
        return em.persist(Order.builder().orderCode(code).platformId(1L)
                .customer(customer).address(address).orderStatus("IN_TRANSIT").status("A").build());
    }

    private DeliveryJob job(String code, String deliveryStatus, String status) {
        return job(code, deliveryStatus, status, null);
    }

    private DeliveryJob job(String code, String deliveryStatus, String status, LocalDateTime completedDate) {
        return em.persist(DeliveryJob.builder().jobCode(code).originSubdistrictCode("470101")
                .deliveryStatus(deliveryStatus).status(status).completedDate(completedDate).totalOrders(0).build());
    }

    private void stop(DeliveryJob job, Order order, String routingStatus) {
        stop(job, order, routingStatus, null);
    }

    private void stop(DeliveryJob job, Order order, String routingStatus, String statusReason) {
        em.persist(DeliveryRouting.builder().deliveryJob(job).order(order).sequenceNo(1)
                .routingStatus(routingStatus).statusReason(statusReason).build());
    }

    private Map<Long, Long> counts(List<Long> orderIds, Long jobId) {
        Map<Long, Long> result = new HashMap<>();
        for (Object[] row : deliveryRoutingRepository.countSkipsByOrder(orderIds, jobId)) {
            result.put((Long) row[0], ((Number) row[1]).longValue());
        }
        return result;
    }

    @Test
    void should_countSkipsOnClosedJobsAndCurrentJob_when_orderWasSkippedBefore() {
        //GIVEN — order A: ข้ามในงานที่ปิดแล้ว 1 ครั้ง + งานปัจจุบัน 1 ครั้ง
        Order a = order("A");
        DeliveryJob closed = job("JOB-CLOSED", "COMPLETED", "A");
        DeliveryJob current = job("JOB-CURRENT", "NEW", "A");
        stop(closed, a, "SKIPPED");
        stop(current, a, "SKIPPED");
        em.flush();

        //WHEN
        Map<Long, Long> result = counts(List.of(a.getOrderId()), current.getJobId());

        //THEN
        assertThat(result).containsEntry(a.getOrderId(), 2L);
    }

    @Test
    void should_ignoreDeletedAndOtherOpenJobsAndNonSkippedStops_when_counting() {
        //GIVEN — มีแค่งานปัจจุบันที่นับ: งานที่ถูกลบ, งานอื่นที่ยังเปิด, จุดที่ส่งแล้ว ไม่นับ
        Order a = order("A");
        DeliveryJob current = job("JOB-CURRENT", "NEW", "A");
        DeliveryJob deleted = job("JOB-DELETED", "COMPLETED", "I");
        DeliveryJob otherOpen = job("JOB-OTHER", "NEW", "A");
        DeliveryJob closedDelivered = job("JOB-DELIVERED", "COMPLETED", "A");
        stop(current, a, "SKIPPED");
        stop(deleted, a, "SKIPPED");
        stop(otherOpen, a, "SKIPPED");
        stop(closedDelivered, a, "COMPLETED");
        em.flush();

        //WHEN
        Map<Long, Long> result = counts(List.of(a.getOrderId()), current.getJobId());

        //THEN
        assertThat(result).containsEntry(a.getOrderId(), 1L);
    }

    @Test
    void should_countEachOrderSeparately_when_manyOrdersAreSkipped() {
        //GIVEN — many: order A ข้าม 2 ครั้ง, order B ข้าม 1 ครั้ง, order C ไม่เคยข้าม
        Order a = order("A");
        Order b = order("B");
        Order c = order("C");
        DeliveryJob closed = job("JOB-CLOSED", "COMPLETED", "A");
        DeliveryJob current = job("JOB-CURRENT", "NEW", "A");
        stop(closed, a, "SKIPPED");
        stop(current, a, "SKIPPED");
        stop(current, b, "SKIPPED");
        stop(current, c, "NEW");
        em.flush();

        //WHEN
        Map<Long, Long> result = counts(List.of(a.getOrderId(), b.getOrderId(), c.getOrderId()), current.getJobId());

        //THEN
        assertThat(result)
                .containsEntry(a.getOrderId(), 2L)
                .containsEntry(b.getOrderId(), 1L)
                .doesNotContainKey(c.getOrderId());
    }

    @Test
    void should_notCountSkipsMadeByClosingTheJob_when_counting() {
        //GIVEN — order A: ข้ามเอง (เหตุผลคนส่ง) 1 + ปิดงานก่อนไปส่ง 1 + แจ้งยกเลิกค้างตอนปิดงาน 1 + ข้อมูลเก่าไม่มีเหตุผล 1
        Order a = order("A");
        DeliveryJob current = job("JOB-CURRENT", "IN_PROGRESS", "A");
        stop(job("JOB-1", "COMPLETED", "A"), a, "SKIPPED", "CUSTOMER_ABSENT");
        stop(job("JOB-2", "COMPLETED", "A"), a, "SKIPPED", "JOB_CLOSED");
        stop(job("JOB-3", "COMPLETED", "A"), a, "SKIPPED", "CANCEL_NOT_APPROVED");
        stop(current, a, "SKIPPED", null);
        em.flush();

        //WHEN
        Map<Long, Long> result = counts(List.of(a.getOrderId()), current.getJobId());

        //THEN — นับเฉพาะที่ข้ามจริง (มีเหตุผลของคนส่ง หรือข้อมูลก่อนมีเหตุผล)
        assertThat(result).containsEntry(a.getOrderId(), 2L);
    }

    @Test
    void should_countOnlySkipsAfterTheReset_when_adminResetTheOrder() {
        //GIVEN — order A ถูกข้าม 2 ครั้งในงานที่ปิดก่อนผู้ดูแลระบบย้อน (เคยเป็น FAILED) แล้วข้ามอีก 1 ครั้งในงานที่ปิดหลังย้อน
        LocalDateTime resetAt = LocalDateTime.of(2026, 11, 7, 10, 0);
        Order a = order("A");
        a.setSkipsResetAt(resetAt);
        stop(job("JOB-OLD-1", "COMPLETED", "A", resetAt.minusDays(3)), a, "SKIPPED", "CUSTOMER_ABSENT");
        stop(job("JOB-OLD-2", "COMPLETED", "A", resetAt.minusDays(1)), a, "SKIPPED", "CUSTOMER_ABSENT");
        stop(job("JOB-AFTER", "COMPLETED", "A", resetAt.plusDays(1)), a, "SKIPPED", "CUSTOMER_ABSENT");
        DeliveryJob current = job("JOB-CURRENT", "IN_PROGRESS", "A");
        stop(current, a, "SKIPPED", "CUSTOMER_ABSENT");
        em.flush();

        //WHEN
        Map<Long, Long> result = counts(List.of(a.getOrderId()), current.getJobId());

        //THEN — ประวัติเก่ายังอยู่ แต่นับแค่งานที่ปิดหลังย้อน + งานที่กำลังปิด (completed_date ยังว่าง)
        assertThat(result).containsEntry(a.getOrderId(), 2L);
    }

    @Test
    void should_countNothingOld_when_resetJobIsClosedAgain() {
        //GIVEN — order A ข้ามในงานเก่า 1 ครั้ง → งานนี้ถูกย้อน (skipsResetAt) → งานนี้กำลังปิดอีกรอบโดยข้าม A
        LocalDateTime resetAt = LocalDateTime.of(2026, 11, 7, 10, 0);
        Order a = order("A");
        a.setSkipsResetAt(resetAt);
        stop(job("JOB-OLD", "COMPLETED", "A", resetAt.minusDays(2)), a, "SKIPPED", "CUSTOMER_ABSENT");
        DeliveryJob reset = job("JOB-RESET", "IN_PROGRESS", "A");
        stop(reset, a, "SKIPPED", "CUSTOMER_ABSENT");
        em.flush();

        //WHEN
        Map<Long, Long> result = counts(List.of(a.getOrderId()), reset.getJobId());

        //THEN — 1 ครั้ง (ไม่ใช่ 2) จึงยังไม่ FAILED
        assertThat(result).containsEntry(a.getOrderId(), 1L);
    }
}
