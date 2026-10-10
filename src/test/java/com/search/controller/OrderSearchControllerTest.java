package com.search.controller;

import com.common.dto.ApiResponse;
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
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * Covers {@link OrderSearchController}: the search is always scoped to the user the security
 * filter identified, never to a {@code createdBy} the client sends.
 */
@ExtendWith(MockitoExtension.class)
class OrderSearchControllerTest {

    @Mock
    private OrderSearchService orderSearchService;

    private OrderSearchController controller;

    @BeforeEach
    void setUp() {
        controller = new OrderSearchController(orderSearchService);
    }

    private OrderSearchRequest searchedRequest() {
        ArgumentCaptor<OrderSearchRequest> captor = ArgumentCaptor.forClass(OrderSearchRequest.class);
        verify(orderSearchService).searchOrders(captor.capture());
        return captor.getValue();
    }

    @Test
    void should_searchAsLoggedInUser_when_filterIdentifiedTheUser() {
        //GIVEN
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.setAttribute("currentUserId", "usera");

        //WHEN
        ResponseEntity<? extends ApiResponse<?>> response = controller.searchOrders(new OrderSearchRequest(), httpRequest);

        //THEN
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(searchedRequest().getCreatedBy()).isEqualTo("usera");
    }

    @Test
    void should_ignoreClientSuppliedCreatedBy_when_clientAsksForAnotherUsersOrders() {
        //GIVEN
        OrderSearchRequest request = new OrderSearchRequest();
        request.setCreatedBy("userb");
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.setAttribute("currentUserId", "usera");

        //WHEN
        controller.searchOrders(request, httpRequest);

        //THEN
        assertThat(searchedRequest().getCreatedBy()).isEqualTo("usera");
    }

    @Test
    void should_clearClientSuppliedCreatedBy_when_filterIsDisabledAndNoUserIsKnown() {
        //GIVEN - default profile: the security filter does not run, so no user attribute
        OrderSearchRequest request = new OrderSearchRequest();
        request.setCreatedBy("userb");

        //WHEN
        controller.searchOrders(request, new MockHttpServletRequest());

        //THEN - unscoped search (every user's orders); see docs/security-check-list.md item 14
        assertThat(searchedRequest().getCreatedBy()).isNull();
    }
}
