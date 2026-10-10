package com.core.controller;

import com.core.dto.OrderRequest;
import com.core.dto.OrderResponse;
import com.core.service.OrderService;
import com.core.service.security.SecurityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers {@link OrderController}: the username is always extracted from the
 * {@code Authorization} header before calling {@link OrderService}, never taken from the body.
 */
@ExtendWith(MockitoExtension.class)
class OrderControllerTest {

    private static final String AUTH_HEADER = "Bearer jwt-token";

    @Mock
    private OrderService orderService;
    @Mock
    private SecurityService securityService;

    private OrderController controller;

    @BeforeEach
    void setUp() {
        controller = new OrderController(orderService, securityService);
        when(securityService.extractUsernameFromToken(AUTH_HEADER)).thenReturn("admin");
    }

    @Test
    void should_createOrderAsExtractedUserAndReturn201_when_creating() {
        //GIVEN
        OrderRequest request = OrderRequest.builder().build();
        OrderResponse created = OrderResponse.builder().orderId(1L).build();
        when(orderService.createOrder(request, "admin")).thenReturn(created);

        //WHEN
        var response = controller.createOrder(AUTH_HEADER, request);

        //THEN
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getData()).isSameAs(created);
        assertThat(response.getBody().getMessage()).isEqualTo("Order created successfully");
    }

    @Test
    void should_updateOrderAsExtractedUserAndReturn200_when_updating() {
        //GIVEN
        OrderRequest request = OrderRequest.builder().build();
        OrderResponse updated = OrderResponse.builder().orderId(1L).build();
        when(orderService.updateOrder(1L, request, "admin")).thenReturn(updated);

        //WHEN
        var response = controller.updateOrder(AUTH_HEADER, 1L, request);

        //THEN
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData()).isSameAs(updated);
        assertThat(response.getBody().getMessage()).isEqualTo("Order updated successfully");
    }

    @Test
    void should_completeOrderAsExtractedUser_when_completing() {
        //GIVEN
        OrderResponse completed = OrderResponse.builder().orderId(1L).build();
        when(orderService.completeOrder(1L, "admin")).thenReturn(completed);

        //WHEN
        var response = controller.completeOrder(AUTH_HEADER, 1L);

        //THEN
        assertThat(response.getBody().getData()).isSameAs(completed);
        assertThat(response.getBody().getMessage()).isEqualTo("Order completed successfully");
    }

    @Test
    void should_cancelOrderAsExtractedUser_when_cancelling() {
        //GIVEN
        OrderResponse cancelled = OrderResponse.builder().orderId(1L).build();
        when(orderService.cancelOrder(1L, "admin")).thenReturn(cancelled);

        //WHEN
        var response = controller.cancelOrder(AUTH_HEADER, 1L);

        //THEN
        assertThat(response.getBody().getData()).isSameAs(cancelled);
        assertThat(response.getBody().getMessage()).isEqualTo("Order cancelled successfully");
    }

    @Test
    void should_deleteOrderAsExtractedUserAndReturnNullData_when_deleting() {
        //GIVEN no stubbing needed for a void call

        //WHEN
        var response = controller.deleteOrder(AUTH_HEADER, 1L);

        //THEN
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData()).isNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Order deleted successfully");
        verify(orderService).deleteOrder(1L, "admin");
    }
}
