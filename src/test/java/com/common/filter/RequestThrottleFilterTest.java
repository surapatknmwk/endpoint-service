package com.common.filter;

import com.authen.constant.SecurityEventType;
import com.authen.service.SecurityEventRecorder;
import com.core.service.security.SecurityService;
import com.github.benmanes.caffeine.cache.Ticker;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Covers {@link RequestThrottleFilter}: the per-IP flood guard and the double-submit guard on
 * writes, with a hand-driven clock.
 */
class RequestThrottleFilterTest {

    private static final int WINDOW_SECONDS = 10;
    private static final int MAX_REQUESTS = 3;
    private static final int DUPLICATE_WINDOW_SECONDS = 3;
    private static final String EXEMPT_POSTS =
            "/endpoint-search-service/**, /endpoint-core-service/api/jobs/route-preview";

    private static final String ORDERS = "/endpoint-core-service/api/orders";
    private static final String COMPLETE_STOP = "/endpoint-core-service/api/jobs/1/stops/5/complete";

    private final AtomicLong nanos = new AtomicLong();
    private final Ticker ticker = nanos::get;
    private final SecurityEventRecorder securityEventRecorder = mock(SecurityEventRecorder.class);
    private final SecurityService securityService = mock(SecurityService.class);

    private RequestThrottleFilter filter =
            new RequestThrottleFilter(true, WINDOW_SECONDS, 100, DUPLICATE_WINDOW_SECONDS, EXEMPT_POSTS,
                    securityEventRecorder, securityService, ticker);

    // ---- double-submit guard ----

    @Test
    void should_passRequest_when_writeIsSentOnce() throws Exception {
        //GIVEN
        MockHttpServletRequest request = request("POST", ORDERS, "token-a");

        //WHEN
        int status = send(request);

        //THEN
        assertThat(status).isEqualTo(200);
        verifyNoInteractions(securityEventRecorder);
    }

    @Test
    void should_respond429_when_sameWriteIsRepeatedWithinDuplicateWindow() throws Exception {
        //GIVEN
        send(request("POST", ORDERS, "token-a"));
        advanceSeconds(DUPLICATE_WINDOW_SECONDS - 1);
        MockHttpServletRequest repeat = request("POST", ORDERS, "token-a");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        when(securityService.extractUsernameFromToken("Bearer token-a")).thenReturn("admin");

        //WHEN
        filter.doFilter(repeat, response, chain);

        //THEN
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(chain.getRequest()).isNull();
        assertThat(response.getHeader("Retry-After")).isEqualTo("3");
        assertThat(response.getContentAsString()).contains(RequestThrottleFilter.DUPLICATE_REQUEST_MESSAGE);
        verify(securityEventRecorder).record(eq(SecurityEventType.DUPLICATE_REQUEST), eq(repeat), eq("admin"), any());
    }

    @Test
    void should_passRequest_when_sameWriteIsRepeatedAfterDuplicateWindow() throws Exception {
        //GIVEN
        send(request("POST", ORDERS, "token-a"));
        advanceSeconds(DUPLICATE_WINDOW_SECONDS);

        //WHEN
        int status = send(request("POST", ORDERS, "token-a"));

        //THEN
        assertThat(status).isEqualTo(200);
    }

    @Test
    void should_respond429ForEveryRepeat_when_sameWriteIsSentManyTimes() throws Exception {
        //GIVEN
        send(request("PATCH", COMPLETE_STOP, "token-a"));

        //WHEN
        int second = send(request("PATCH", COMPLETE_STOP, "token-a"));
        int third = send(request("PATCH", COMPLETE_STOP, "token-a"));
        int fourth = send(request("PATCH", COMPLETE_STOP, "token-a"));

        //THEN
        assertThat(new int[]{second, third, fourth}).containsOnly(429);
    }

    @Test
    void should_passRequest_when_writeTargetsDifferentUrlOrMethodOrQuery() throws Exception {
        //GIVEN
        send(request("PATCH", "/endpoint-core-service/api/orders/cancel/1", "token-a"));
        MockHttpServletRequest withQuery = request("PATCH", "/endpoint-core-service/api/orders/cancel/1", "token-a");
        withQuery.setQueryString("reason=x");

        //WHEN
        int otherId = send(request("PATCH", "/endpoint-core-service/api/orders/cancel/2", "token-a"));
        int otherMethod = send(request("DELETE", "/endpoint-core-service/api/orders/cancel/1", "token-a"));
        int otherQuery = send(withQuery);

        //THEN
        assertThat(new int[]{otherId, otherMethod, otherQuery}).containsOnly(200);
    }

    @Test
    void should_passRequest_when_sameWriteComesFromDifferentToken() throws Exception {
        //GIVEN
        send(request("POST", ORDERS, "token-a"));

        //WHEN
        int status = send(request("POST", ORDERS, "token-b"));

        //THEN
        assertThat(status).isEqualTo(200);
    }

    @Test
    void should_respond429_when_anonymousWriteIsRepeatedFromSameIp() throws Exception {
        //GIVEN
        send(request("POST", ORDERS, null));

        //WHEN
        int status = send(request("POST", ORDERS, null));

        //THEN
        assertThat(status).isEqualTo(429);
    }

    @Test
    void should_passRepeatedRequest_when_methodIsGet() throws Exception {
        //GIVEN
        send(request("GET", "/endpoint-core-service/api/jobs", "token-a"));

        //WHEN
        int status = send(request("GET", "/endpoint-core-service/api/jobs", "token-a"));

        //THEN
        assertThat(status).isEqualTo(200);
    }

    @Test
    void should_passRepeatedRequest_when_postIsExempt() throws Exception {
        //GIVEN
        send(request("POST", "/endpoint-search-service/api/search/orders", "token-a"));
        send(request("POST", "/endpoint-core-service/api/jobs/route-preview", "token-a"));

        //WHEN
        int search = send(request("POST", "/endpoint-search-service/api/search/orders", "token-a"));
        int routePreview = send(request("POST", "/endpoint-core-service/api/jobs/route-preview", "token-a"));

        //THEN
        assertThat(new int[]{search, routePreview}).containsOnly(200);
    }

    @Test
    void should_passRepeatedRequest_when_pathIsAuthenModule() throws Exception {
        //GIVEN
        send(request("POST", "/endpoint-authen-service/auth/login", null));

        //WHEN
        int status = send(request("POST", "/endpoint-authen-service/auth/login", null));

        //THEN
        assertThat(status).isEqualTo(200);
    }

    @Test
    void should_allowImmediateRetry_when_firstWriteEndedWithErrorStatus() throws Exception {
        //GIVEN
        FilterChain failingChain = (req, res) -> ((MockHttpServletResponse) res).setStatus(400);
        filter.doFilter(request("POST", ORDERS, "token-a"), new MockHttpServletResponse(), failingChain);

        //WHEN
        int status = send(request("POST", ORDERS, "token-a"));

        //THEN
        assertThat(status).isEqualTo(200);
    }

    @Test
    void should_allowImmediateRetry_when_firstWriteThrew() throws Exception {
        //GIVEN
        FilterChain throwingChain = (req, res) -> {
            throw new ServletException("boom");
        };
        assertThatThrownBy(() ->
                filter.doFilter(request("POST", ORDERS, "token-a"), new MockHttpServletResponse(), throwingChain))
                .isInstanceOf(ServletException.class);

        //WHEN
        int status = send(request("POST", ORDERS, "token-a"));

        //THEN
        assertThat(status).isEqualTo(200);
    }

    // ---- flood guard ----

    @Test
    void should_passRequests_when_countIsExactlyAtLimit() throws Exception {
        //GIVEN
        useFloodLimit(MAX_REQUESTS);

        //WHEN
        int first = send(request("GET", "/endpoint-core-service/api/jobs", "token-a"));
        int second = send(request("GET", "/endpoint-master-service/api/provinces", "token-a"));
        int third = send(request("POST", "/endpoint-authen-service/auth/login", null));

        //THEN
        assertThat(new int[]{first, second, third}).containsOnly(200);
    }

    @Test
    void should_respond429_when_ipExceedsLimitWithinWindow() throws Exception {
        //GIVEN
        useFloodLimit(MAX_REQUESTS);
        for (int i = 0; i < MAX_REQUESTS; i++) {
            send(request("GET", "/endpoint-core-service/api/jobs", "token-a"));
        }
        advanceSeconds(WINDOW_SECONDS - 1);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        //WHEN
        filter.doFilter(request("GET", "/endpoint-master-service/api/provinces", "token-b"), response, chain);

        //THEN
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(chain.getRequest()).isNull();
        assertThat(response.getHeader("Retry-After")).isEqualTo("10");
        assertThat(response.getContentAsString()).contains(RequestThrottleFilter.TOO_MANY_REQUESTS_MESSAGE);
        verify(securityEventRecorder).record(eq(SecurityEventType.RATE_LIMIT_EXCEEDED), eq("127.0.0.1"),
                isNull(), isNull(), isNull(), isNull(), any());
    }

    @Test
    void should_passRequest_when_windowHasElapsedAfterLimitWasHit() throws Exception {
        //GIVEN
        useFloodLimit(MAX_REQUESTS);
        for (int i = 0; i <= MAX_REQUESTS; i++) {
            send(request("GET", "/endpoint-core-service/api/jobs", "token-a"));
        }
        advanceSeconds(WINDOW_SECONDS);

        //WHEN
        int status = send(request("GET", "/endpoint-core-service/api/jobs", "token-a"));

        //THEN
        assertThat(status).isEqualTo(200);
    }

    @Test
    void should_countEachIpSeparately_when_requestsComeThroughProxy() throws Exception {
        //GIVEN
        useFloodLimit(MAX_REQUESTS);
        for (int i = 0; i <= MAX_REQUESTS; i++) {
            send(forwardedFor(request("GET", "/endpoint-core-service/api/jobs", "token-a"), "203.0.113.1"));
        }

        //WHEN
        int floodingIp = send(forwardedFor(request("GET", "/endpoint-core-service/api/jobs", "token-a"), "203.0.113.1"));
        int otherIp = send(forwardedFor(request("GET", "/endpoint-core-service/api/jobs", "token-a"), "203.0.113.2, 10.0.0.1"));

        //THEN
        assertThat(floodingIp).isEqualTo(429);
        assertThat(otherIp).isEqualTo(200);
    }

    @Test
    void should_notCountRequest_when_pathIsOutsideApiModulesOrPreflight() throws Exception {
        //GIVEN
        useFloodLimit(MAX_REQUESTS);
        for (int i = 0; i < MAX_REQUESTS * 2; i++) {
            send(request("GET", "/actuator/health", null));
            send(request("OPTIONS", "/endpoint-core-service/api/jobs", null));
        }

        //WHEN
        int status = send(request("GET", "/endpoint-core-service/api/jobs", "token-a"));

        //THEN
        assertThat(status).isEqualTo(200);
    }

    @Test
    void should_passEverything_when_throttleIsDisabled() throws Exception {
        //GIVEN
        filter = new RequestThrottleFilter(false, WINDOW_SECONDS, 1, DUPLICATE_WINDOW_SECONDS, EXEMPT_POSTS,
                    securityEventRecorder, securityService, ticker);

        //WHEN
        int first = send(request("POST", ORDERS, "token-a"));
        int second = send(request("POST", ORDERS, "token-a"));
        int third = send(request("POST", ORDERS, "token-a"));

        //THEN
        assertThat(new int[]{first, second, third}).containsOnly(200);
    }

    private void useFloodLimit(int maxRequests) {
        filter = new RequestThrottleFilter(true, WINDOW_SECONDS, maxRequests, DUPLICATE_WINDOW_SECONDS, EXEMPT_POSTS,
                    securityEventRecorder, securityService, ticker);
    }

    private void advanceSeconds(long seconds) {
        nanos.addAndGet(TimeUnit.SECONDS.toNanos(seconds));
    }

    private int send(MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response.getStatus();
    }

    private MockHttpServletRequest request(String method, String uri, String token) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        if (token != null) {
            request.addHeader("Authorization", "Bearer " + token);
        }
        return request;
    }

    private MockHttpServletRequest forwardedFor(MockHttpServletRequest request, String value) {
        request.addHeader("X-Forwarded-For", value);
        return request;
    }
}
