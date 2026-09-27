package com.core.service;

import com.core.dto.OrderRequest;
import com.core.dto.OrderResponse;
import com.core.dto.master.AddressInfoDto;
import com.core.entity.Customer;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Covers {@link OrderService#updateOrder}: editing an order must never move its
 * orderStatus (that only happens through complete/cancel and {@link JobService}).
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
                .address(OrderAddress.builder().addressId(20L).addressLine("old").provinceCode("47").build())
                .orderStatus(orderStatus)
                .sequenceNo(sequenceNo)
                .build();
    }

    private OrderRequest editRequest(String orderStatus, Integer sequenceNo) {
        return OrderRequest.builder()
                .customer(OrderRequest.CustomerInfo.builder().name("ใหม่").build())
                .address(OrderRequest.AddressInfo.builder().addressLine("new").provinceCode("47").build())
                .orderStatus(orderStatus)
                .sequenceNo(sequenceNo)
                .build();
    }

    private void stubSaveAndLookup(Order order) {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(masterDataService.getAddressInfoSafe(any(), any(), any())).thenReturn(AddressInfoDto.builder().build());
    }

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
}
