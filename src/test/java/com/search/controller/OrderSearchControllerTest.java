package com.search.controller;

import com.common.dto.ApiResponse;
import com.core.service.security.SecurityService;
import com.search.dto.request.OrderSearchRequest;
import com.search.service.OrderSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers {@link OrderSearchController}: the search is always scoped to the user in the token,
 * never to a {@code createdBy} the client sends — also when the security filter is off.
 */
@ExtendWith(MockitoExtension.class)
class OrderSearchControllerTest {

    private static final String AUTH = "Bearer token";

    @Mock
    private OrderSearchService orderSearchService;

    @Mock
    private SecurityService securityService;

    private OrderSearchController controller;

    @BeforeEach
    void setUp() {
        controller = new OrderSearchController(orderSearchService, securityService);
    }

    private OrderSearchRequest searchedRequest() {
        ArgumentCaptor<OrderSearchRequest> captor = ArgumentCaptor.forClass(OrderSearchRequest.class);
        verify(orderSearchService).searchOrders(captor.capture());
        return captor.getValue();
    }

    @Test
    void should_searchAsLoggedInUser_when_tokenIdentifiesTheUser() {
        //GIVEN
        when(securityService.extractUsernameFromToken(AUTH)).thenReturn("usera");

        //WHEN
        ResponseEntity<? extends ApiResponse<?>> response = controller.searchOrders(AUTH, new OrderSearchRequest());

        //THEN
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(searchedRequest().getCreatedBy()).isEqualTo("usera");
    }

    @Test
    void should_ignoreClientSuppliedCreatedBy_when_clientAsksForAnotherUsersOrders() {
        //GIVEN
        OrderSearchRequest request = new OrderSearchRequest();
        request.setCreatedBy("userb");
        when(securityService.extractUsernameFromToken(AUTH)).thenReturn("usera");

        //WHEN
        controller.searchOrders(AUTH, request);

        //THEN
        assertThat(searchedRequest().getCreatedBy()).isEqualTo("usera");
    }

    @Test
    void should_clearClientSuppliedCreatedBy_when_tokenHasNoUser() {
        //GIVEN
        OrderSearchRequest request = new OrderSearchRequest();
        request.setCreatedBy("userb");
        when(securityService.extractUsernameFromToken(AUTH)).thenReturn(null);

        //WHEN
        controller.searchOrders(AUTH, request);

        //THEN - OrderSearchService then returns no orders
        assertThat(searchedRequest().getCreatedBy()).isNull();
    }
}
