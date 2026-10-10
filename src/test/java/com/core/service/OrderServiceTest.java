package com.core.service;

import com.core.dto.OrderRequest;
import com.core.dto.OrderResponse;
import com.core.dto.master.AddressInfoDto;
import com.core.entity.Customer;
import com.core.entity.DeliveryRouting;
import com.core.entity.Order;
import com.core.entity.OrderAddress;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the order status rules in {@link OrderService}: an order's orderStatus only moves through
 * complete/cancel here and through {@link JobService}; create/edit never take it from the request.
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

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

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, customerRepository, orderAddressRepository,
                deliveryRoutingRepository, masterDataService);
    }

    private Order storedOrder(String orderStatus, int sequenceNo) {
        return Order.builder()
                .orderId(1L)
                .orderCode("ORD-1")
                .customer(Customer.builder().customerId(10L).name("เดิม").build())
                .address(OrderAddress.builder().addressId(20L).addressLine("old")
                        .provinceCode("47").districtCode("4701").subdistrictCode("470101").zipCode("47000")
                        .build())
                .orderStatus(orderStatus)
                .sequenceNo(sequenceNo)
                .createdBy("operator")
                .build();
    }

    private OrderRequest editRequest(String orderStatus, Integer sequenceNo) {
        return OrderRequest.builder()
                .customer(OrderRequest.CustomerInfo.builder().name("ใหม่").build())
                .address(OrderRequest.AddressInfo.builder().addressLine("new")
                        .provinceCode("47").districtCode("4701").subdistrictCode("470101").zipCode("47000")
                        .build())
                .orderStatus(orderStatus)
                .sequenceNo(sequenceNo)
                .build();
    }

    private OrderRequest relocateRequest() {
        return OrderRequest.builder()
                .customer(OrderRequest.CustomerInfo.builder().name("ใหม่").build())
                .address(OrderRequest.AddressInfo.builder().addressLine("new")
                        .provinceCode("10").districtCode("1001").subdistrictCode("100101").zipCode("10200")
                        .build())
                .build();
    }

    private void stubSaveAndLookup(Order order) {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(masterDataService.getAddressInfoSafe(any(), any(), any())).thenReturn(AddressInfoDto.builder().build());
    }

    // ---------- createOrder ----------

    @Test
    void should_createOrderAsNew_when_requestSendsAnotherStatus() {
        //GIVEN - a client tries to create an order straight into a job state
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        //WHEN
        OrderResponse result = orderService.createOrder(editRequest("IN_TRANSIT", null), "operator");

        //THEN
        assertThat(result.getOrderStatus()).isEqualTo("NEW");
    }

    // ---------- updateOrder ----------

    @Test
    void should_keepStoredOrderStatus_when_requestSendsADifferentStatus() {
        //GIVEN - an order already on a job, and an edit request that still carries "NEW"
        Order order = storedOrder("IN_TRANSIT", 3);
        stubSaveAndLookup(order);

        //WHEN
        OrderResponse result = orderService.updateOrder(1L, editRequest("NEW", null), "operator");

        //THEN - status untouched, the other fields are still updated
        assertThat(order.getOrderStatus()).isEqualTo("IN_TRANSIT");
        assertThat(result.getOrderStatus()).isEqualTo("IN_TRANSIT");
        assertThat(order.getCustomer().getName()).isEqualTo("ใหม่");
        assertThat(order.getAddress().getAddressLine()).isEqualTo("new");
    }

    @Test
    void should_keepStoredSequenceNo_when_requestOmitsSequenceNo() {
        //GIVEN - the webapp no longer sends sequenceNo/orderStatus on edit
        Order order = storedOrder("PENDING", 3);
        stubSaveAndLookup(order);

        //WHEN
        orderService.updateOrder(1L, editRequest(null, null), "operator");

        //THEN
        assertThat(order.getSequenceNo()).isEqualTo(3);
        assertThat(order.getOrderStatus()).isEqualTo("PENDING");
    }

    @ParameterizedTest
    @ValueSource(strings = {"COMPLETED", "CANCELLED", "FAILED"})
    void should_rejectEditAndSaveNothing_when_orderIsClosed(String status) {
        //GIVEN
        when(orderRepository.findById(1L)).thenReturn(Optional.of(storedOrder(status, 1)));

        //WHEN / THEN
        assertThatThrownBy(() -> orderService.updateOrder(1L, editRequest(null, null), "operator"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be edited");
        verify(customerRepository, never()).save(any());
        verify(orderAddressRepository, never()).save(any());
        verify(orderRepository, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ASSIGNED", "IN_TRANSIT"})
    void should_keepStoredLocation_when_orderIsOnOpenJob(String status) {
        //GIVEN - the order is on an open job (started or not), so its location is already counted in the route
        Order order = storedOrder(status, 1);
        stubSaveAndLookup(order);

        //WHEN
        orderService.updateOrder(1L, relocateRequest(), "operator");

        //THEN - location fields stay, free-text address line is still editable
        OrderAddress address = order.getAddress();
        assertThat(address.getProvinceCode()).isEqualTo("47");
        assertThat(address.getDistrictCode()).isEqualTo("4701");
        assertThat(address.getSubdistrictCode()).isEqualTo("470101");
        assertThat(address.getZipCode()).isEqualTo("47000");
        assertThat(address.getAddressLine()).isEqualTo("new");
    }

    @ParameterizedTest
    @ValueSource(strings = {"NEW", "PENDING"})
    void should_updateLocation_when_orderIsNotOnAnOpenJob(String status) {
        //GIVEN - PENDING was skipped on a job that is already closed, so its address can be fixed
        Order order = storedOrder(status, 1);
        stubSaveAndLookup(order);

        //WHEN
        orderService.updateOrder(1L, relocateRequest(), "operator");

        //THEN
        OrderAddress address = order.getAddress();
        assertThat(address.getProvinceCode()).isEqualTo("10");
        assertThat(address.getDistrictCode()).isEqualTo("1001");
        assertThat(address.getSubdistrictCode()).isEqualTo("100101");
        assertThat(address.getZipCode()).isEqualTo("10200");
    }

    // ---------- completeOrder ----------

    @ParameterizedTest
    @ValueSource(strings = {"NEW", "PENDING"})
    void should_completeAndStampDeliveryDate_when_orderIsNotOnAJob(String status) {
        //GIVEN
        Order order = storedOrder(status, 1);
        stubSaveAndLookup(order);

        //WHEN
        OrderResponse result = orderService.completeOrder(1L, "operator");

        //THEN
        assertThat(result.getOrderStatus()).isEqualTo("COMPLETED");
        assertThat(order.getDeliveryDate()).isNotNull();
        assertThat(order.getUpdatedBy()).isEqualTo("operator");
    }

    @ParameterizedTest
    @ValueSource(strings = {"IN_TRANSIT", "COMPLETED", "CANCELLED"})
    void should_rejectCompleteAndSaveNothing_when_statusIsNotCompletable(String status) {
        //GIVEN - IN_TRANSIT must be completed through the job stop; closed orders stay closed
        Order order = storedOrder(status, 1);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        //WHEN / THEN
        assertThatThrownBy(() -> orderService.completeOrder(1L, "operator"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be completed");
        assertThat(order.getOrderStatus()).isEqualTo(status);
        verify(orderRepository, never()).save(any());
    }

    // ---------- cancelOrder ----------

    @ParameterizedTest
    @ValueSource(strings = {"NEW", "PENDING"})
    void should_cancel_when_orderIsNotOnAnOpenJob(String status) {
        //GIVEN
        Order order = storedOrder(status, 1);
        stubSaveAndLookup(order);

        //WHEN
        OrderResponse result = orderService.cancelOrder(1L, "operator");

        //THEN
        assertThat(result.getOrderStatus()).isEqualTo("CANCELLED");
        assertThat(order.getUpdatedBy()).isEqualTo("operator");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ASSIGNED", "IN_TRANSIT", "COMPLETED", "CANCELLED", "FAILED"})
    void should_rejectCancelAndSaveNothing_when_orderIsOnJobOrFinished(String status) {
        //GIVEN - IN_TRANSIT must be removed from its job first; closed orders stay closed
        Order order = storedOrder(status, 1);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        //WHEN / THEN
        assertThatThrownBy(() -> orderService.cancelOrder(1L, "operator"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be cancelled");
        assertThat(order.getOrderStatus()).isEqualTo(status);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void should_throwNotFound_when_cancellingUnknownOrder() {
        //GIVEN
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        //WHEN / THEN
        assertThatThrownBy(() -> orderService.cancelOrder(99L, "operator"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Order not found: 99");
    }

    // ---------- deleteOrder ----------

    @ParameterizedTest
    @ValueSource(strings = {"ASSIGNED", "IN_TRANSIT"})
    void should_rejectDeleteAndRemoveNothing_when_orderIsOnAnActiveJob(String status) {
        //GIVEN - ASSIGNED is on a job that has not left yet, still on the job
        when(orderRepository.findById(1L)).thenReturn(Optional.of(storedOrder(status, 1)));

        //WHEN / THEN
        assertThatThrownBy(() -> orderService.deleteOrder(1L, "operator"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be deleted");
        verify(deliveryRoutingRepository, never()).deleteAll(any());
        verify(orderRepository, never()).delete(any(Order.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"NEW", "PENDING", "COMPLETED", "CANCELLED", "FAILED"})
    void should_deleteRoutingsOrderCustomerAndAddress_when_orderIsNotOnAnActiveJob(String status) {
        //GIVEN - e.g. a PENDING order still has the SKIPPED routing row of its closed job
        Order order = storedOrder(status, 1);
        DeliveryRouting skippedStop = DeliveryRouting.builder().order(order).build();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(deliveryRoutingRepository.findByOrderOrderId(1L)).thenReturn(List.of(skippedStop));

        //WHEN
        orderService.deleteOrder(1L, "operator");

        //THEN
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<DeliveryRouting>> routings = ArgumentCaptor.forClass(Iterable.class);
        verify(deliveryRoutingRepository).deleteAll(routings.capture());
        assertThat(routings.getValue()).containsExactly(skippedStop);
        verify(orderRepository).delete(order);
        verify(customerRepository).delete(order.getCustomer());
        verify(orderAddressRepository).delete(order.getAddress());
    }

    // ---------- per-user isolation: an order belongs to the user who created it ----------

    @Test
    void should_reportNotFoundAndChangeNothing_when_orderBelongsToAnotherUser() {
        //GIVEN
        Order order = storedOrder("NEW", 1);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        //WHEN / THEN
        assertThatThrownBy(() -> orderService.updateOrder(1L, editRequest(null, null), "intruder"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Order not found: 1");
        assertThatThrownBy(() -> orderService.completeOrder(1L, "intruder"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Order not found: 1");
        assertThatThrownBy(() -> orderService.cancelOrder(1L, "intruder"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Order not found: 1");
        assertThatThrownBy(() -> orderService.deleteOrder(1L, "intruder"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Order not found: 1");
        assertThat(order.getOrderStatus()).isEqualTo("NEW");
        assertThat(order.getCustomer().getName()).isEqualTo("เดิม");
        verify(orderRepository, never()).save(any());
        verify(orderRepository, never()).delete(any(Order.class));
    }

    @Test
    void should_reportNotFound_when_usernameIsUnknown() {
        //GIVEN
        when(orderRepository.findById(1L)).thenReturn(Optional.of(storedOrder("NEW", 1)));

        //WHEN / THEN
        assertThatThrownBy(() -> orderService.cancelOrder(1L, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Order not found: 1");
    }

    @Test
    void should_reportNotFound_when_orderHasNoOwner() {
        //GIVEN
        Order orphan = storedOrder("NEW", 1);
        orphan.setCreatedBy(null);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(orphan));

        //WHEN / THEN
        assertThatThrownBy(() -> orderService.cancelOrder(1L, "operator"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Order not found: 1");
    }
}
