package com.core.service;

import com.core.entity.DeliveryJob;
import com.core.entity.DeliveryRouting;
import com.core.entity.Order;
import com.core.repository.CustomerRepository;
import com.core.repository.DeliveryRoutingRepository;
import com.core.repository.OrderAddressRepository;
import com.core.repository.OrderRepository;
import com.core.service.clients.MasterDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the admin side of {@link OrderService} (admin.md US-ADM-03).
 * <ul>
 *   <li>WHAT — resetOrderAsAdmin puts an order back to NEW (delivery date, reminded appointment and skip counter
 *   cleared — the counter by stamping skipsResetAt, the skip history stays);
 *   deleteOrderAsAdmin hides it (status 'I'), the row stays.</li>
 *   <li>WHEN — any owner's active order; refused while the order is on an active job that is not closed;
 *   reset refused when already NEW.</li>
 *   <li>THEN — the fields written, the 400 message, nothing saved on a refusal.</li>
 * </ul>
 * ZOMBIES: Many / Boundary skipped — one order per call, no limits.
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceAdminTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 7, 10, 0);
    private static final Clock CLOCK = Clock.fixed(NOW.atZone(ZoneId.of("Asia/Bangkok")).toInstant(),
            ZoneId.of("Asia/Bangkok"));

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private OrderAddressRepository orderAddressRepository;
    @Mock
    private DeliveryRoutingRepository deliveryRoutingRepository;
    @Mock
    private MasterDataService masterDataService;
    @Mock
    private AreaSelectionGuard areaSelectionGuard;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, customerRepository, orderAddressRepository,
                deliveryRoutingRepository, masterDataService, CLOCK, areaSelectionGuard, mock(OrderGeoLocator.class));
    }

    private Order givenOrder(String orderStatus, DeliveryJob... jobs) {
        Order order = Order.builder().orderId(1L).orderCode("ORD-1").orderStatus(orderStatus).status("A")
                .deliveryDate(LocalDateTime.of(2026, 11, 6, 15, 0))
                .appointmentDate(LocalDate.of(2026, 11, 8)).appointmentRemindedDate(LocalDate.of(2026, 11, 8))
                .createdBy("owner1").build();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        List<DeliveryRouting> routings = java.util.Arrays.stream(jobs)
                .map(j -> DeliveryRouting.builder().deliveryJob(j).order(order).build()).toList();
        when(deliveryRoutingRepository.findByOrderOrderId(1L)).thenReturn(routings);
        return order;
    }

    private static DeliveryJob job(String deliveryStatus, String status) {
        return DeliveryJob.builder().jobId(5L).jobCode("JOB-5").deliveryStatus(deliveryStatus).status(status).build();
    }

    // ---- resetOrderAsAdmin ----

    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "COMPLETED", "CANCELLED", "FAILED"})
    void should_putOrderBackToNew_when_orderIsNotOnAnOpenJob(String orderStatus) {
        //GIVEN
        Order order = givenOrder(orderStatus, job("COMPLETED", "A"), job("IN_PROGRESS", "I"));

        //WHEN
        orderService.resetOrderAsAdmin(1L, "god");

        //THEN
        assertThat(order.getOrderStatus()).isEqualTo("NEW");
        assertThat(order.getDeliveryDate()).isNull();
        assertThat(order.getAppointmentRemindedDate()).isNull();
        assertThat(order.getAppointmentDate()).isEqualTo(LocalDate.of(2026, 11, 8));
        assertThat(order.getSkipsResetAt()).isEqualTo(NOW); // ข้ามกี่ครั้งก่อนหน้านี้ ไม่นับแล้ว
        assertThat(order.getUpdatedBy()).isEqualTo("god");
        verify(orderRepository).save(order);
    }

    @ParameterizedTest
    @ValueSource(strings = {"NEW", "IN_PROGRESS"})
    void should_refuseReset_when_orderIsOnAnOpenJob(String jobStatus) {
        //GIVEN
        givenOrder("ASSIGNED", job(jobStatus, "A"));

        //WHEN / THEN
        assertThatThrownBy(() -> orderService.resetOrderAsAdmin(1L, "god"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JOB-5");
        verify(orderRepository, never()).save(any());
    }

    @Test
    void should_refuseReset_when_orderIsAlreadyNew() {
        //GIVEN
        Order order = Order.builder().orderId(1L).orderCode("ORD-1").orderStatus("NEW").status("A").build();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        //WHEN / THEN
        assertThatThrownBy(() -> orderService.resetOrderAsAdmin(1L, "god"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ขั้นแรก");
        verify(orderRepository, never()).save(any());
    }

    // ---- deleteOrderAsAdmin ----

    @Test
    void should_hideOrder_when_adminDeletesOrderOfAClosedJob() {
        //GIVEN
        Order order = givenOrder("COMPLETED", job("COMPLETED", "A"));

        //WHEN
        orderService.deleteOrderAsAdmin(1L, "god");

        //THEN
        assertThat(order.getStatus()).isEqualTo("I");
        assertThat(order.getOrderStatus()).isEqualTo("COMPLETED");
        verify(orderRepository).save(order);
        verify(deliveryRoutingRepository, never()).deleteAll(any());
    }

    @Test
    void should_refuseDelete_when_orderIsOnAnOpenJob() {
        //GIVEN
        givenOrder("IN_TRANSIT", job("IN_PROGRESS", "A"));

        //WHEN / THEN
        assertThatThrownBy(() -> orderService.deleteOrderAsAdmin(1L, "god"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JOB-5");
        verify(orderRepository, never()).save(any());
    }

    @Test
    void should_answerNotFound_when_orderIsAlreadyDeleted() {
        //GIVEN
        Order order = Order.builder().orderId(1L).orderStatus("NEW").status("I").build();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        //WHEN / THEN
        assertThatThrownBy(() -> orderService.deleteOrderAsAdmin(1L, "god"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Order not found");
    }
}
