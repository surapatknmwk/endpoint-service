package com.core.repository;

import com.core.entity.Customer;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs {@link OrderRepository#findCandidateOrdersForJob} against a real PostgreSQL: the JPQL uses
 * implicit joins, {@code :param IS NULL} with null String binds and a CASE in ORDER BY, none of
 * which a Mockito mock can prove. Skipped when Docker is not available.
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class OrderRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = Order.class)
    @EnableJpaRepositories(basePackageClasses = OrderRepository.class)
    static class JpaSliceConfig {
    }

    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private TestEntityManager em;

    private static final LocalDateTime BASE = LocalDateTime.of(2026, 1, 1, 8, 0);

    private Order persist(String code, String orderStatus, String status,
                          String province, String district, String subdistrict, int minutesAfterBase) {
        Customer customer = em.persist(Customer.builder().name("c-" + code).build());
        OrderAddress address = em.persist(OrderAddress.builder().addressLine("a-" + code)
                .provinceCode(province).districtCode(district).subdistrictCode(subdistrict).build());
        Order order = em.persist(Order.builder().orderCode(code).platformId(1L)
                .customer(customer).address(address).orderStatus(orderStatus).status(status).createdBy("admin").build());
        em.flush();
        // @PrePersist stamps now(); pin created_at so "oldest first" is deterministic
        em.getEntityManager().createNativeQuery("UPDATE orders SET created_at = ?1 WHERE order_id = ?2")
                .setParameter(1, BASE.plusMinutes(minutesAfterBase))
                .setParameter(2, order.getOrderId())
                .executeUpdate();
        return order;
    }

    private List<String> codes(List<Order> orders) {
        return orders.stream().map(Order::getOrderCode).toList();
    }

    @Test
    void should_returnOnlyActiveNewOrPendingOrdersInProvince_when_noDistrictFilter() {
        //GIVEN
        persist("NEW-1", "NEW", "A", "47", "4701", "470101", 1);
        persist("PEN-1", "PENDING", "A", "47", "4702", "470201", 2);
        persist("TRN-1", "IN_TRANSIT", "A", "47", "4701", "470101", 3);
        persist("DON-1", "COMPLETED", "A", "47", "4701", "470101", 4);
        persist("CXL-1", "CANCELLED", "A", "47", "4701", "470101", 5);
        persist("INA-1", "NEW", "I", "47", "4701", "470101", 6);
        persist("OTH-1", "NEW", "A", "10", "1001", "100101", 7);
        em.clear();

        //WHEN - district/subdistrict bound as null
        List<Order> result = orderRepository.findCandidateOrdersForJob(Set.of("47"), null, null, "admin", PageRequest.of(0, 500));

        //THEN
        assertThat(codes(result)).containsExactlyInAnyOrder("NEW-1", "PEN-1");
    }

    @Test
    void should_narrowToDistrictAndSubdistrict_when_filtersGiven() {
        //GIVEN
        persist("D1-S1", "NEW", "A", "47", "4701", "470101", 1);
        persist("D1-S2", "NEW", "A", "47", "4701", "470102", 2);
        persist("D2-S1", "NEW", "A", "47", "4702", "470201", 3);
        em.clear();

        //WHEN
        List<Order> byDistrict = orderRepository.findCandidateOrdersForJob(Set.of("47"), "4701", null, "admin", PageRequest.of(0, 500));
        List<Order> bySubdistrict = orderRepository.findCandidateOrdersForJob(Set.of("47"), "4701", "470102", "admin", PageRequest.of(0, 500));

        //THEN
        assertThat(codes(byDistrict)).containsExactlyInAnyOrder("D1-S1", "D1-S2");
        assertThat(codes(bySubdistrict)).containsExactly("D1-S2");
    }

    @Test
    void should_putPendingFirstThenOldestFirst_andCutAtPageSize() {
        //GIVEN - created_at order deliberately differs from status order
        persist("NEW-OLD", "NEW", "A", "47", "4701", "470101", 1);
        persist("PEN-NEW", "PENDING", "A", "47", "4701", "470101", 30);
        persist("NEW-MID", "NEW", "A", "47", "4701", "470101", 10);
        persist("PEN-OLD", "PENDING", "A", "47", "4701", "470101", 5);
        persist("NEW-LATE", "NEW", "A", "47", "4701", "470101", 60);
        em.clear();

        //WHEN
        List<Order> all = orderRepository.findCandidateOrdersForJob(Set.of("47"), null, null, "admin", PageRequest.of(0, 500));
        List<Order> firstThree = orderRepository.findCandidateOrdersForJob(Set.of("47"), null, null, "admin", PageRequest.of(0, 3));

        //THEN
        assertThat(codes(all)).containsExactly("PEN-OLD", "PEN-NEW", "NEW-OLD", "NEW-MID", "NEW-LATE");
        assertThat(codes(firstThree)).containsExactly("PEN-OLD", "PEN-NEW", "NEW-OLD");
    }

    @Test
    void should_loadAddressAndCustomerWithoutExtraQueries_when_returningCandidates() {
        //GIVEN
        persist("NEW-1", "NEW", "A", "47", "4701", "470101", 1);
        em.clear();

        //WHEN
        List<Order> result = orderRepository.findCandidateOrdersForJob(Set.of("47"), null, null, "admin", PageRequest.of(0, 500));
        em.clear(); // detach: touching a lazy proxy now would throw if it was not fetched

        //THEN
        assertThat(result.get(0).getAddress().getSubdistrictCode()).isEqualTo("470101");
        assertThat(result.get(0).getCustomer().getName()).isEqualTo("c-NEW-1");
    }

    @Test
    void should_returnOnlyTheGivenUsersOrders_when_otherUsersHaveOrdersInTheSameArea() {
        //GIVEN
        persist("MINE-1", "NEW", "A", "47", "4701", "470101", 1);
        Order others = persist("OTHERS-1", "NEW", "A", "47", "4701", "470101", 2);
        Order ownerless = persist("NO-OWNER", "NEW", "A", "47", "4701", "470101", 3);
        em.getEntityManager().createNativeQuery("UPDATE orders SET created_by = 'other' WHERE order_id = ?1")
                .setParameter(1, others.getOrderId()).executeUpdate();
        em.getEntityManager().createNativeQuery("UPDATE orders SET created_by = NULL WHERE order_id = ?1")
                .setParameter(1, ownerless.getOrderId()).executeUpdate();
        em.clear();

        //WHEN
        List<Order> mine = orderRepository.findCandidateOrdersForJob(Set.of("47"), null, null, "admin", PageRequest.of(0, 500));
        List<Order> theirs = orderRepository.findCandidateOrdersForJob(Set.of("47"), null, null, "other", PageRequest.of(0, 500));
        List<Order> nobody = orderRepository.findCandidateOrdersForJob(Set.of("47"), null, null, "stranger", PageRequest.of(0, 500));

        //THEN
        assertThat(codes(mine)).containsExactly("MINE-1");
        assertThat(codes(theirs)).containsExactly("OTHERS-1");
        assertThat(nobody).isEmpty();
    }
}
