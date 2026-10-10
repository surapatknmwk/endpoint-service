package com.common.filter;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers {@link CorsAllowlistFilter}: CORS headers go only to allow-listed origins, preflights
 * from other origins are rejected, and same-origin / proxied requests are never blocked.
 */
class CorsAllowlistFilterTest {

    private static final String WEBAPP = "https://app.example.com";
    private static final String FOREIGN = "https://evil.example.net";

    @Test
    void should_continueWithoutCorsHeaders_when_requestHasNoOrigin() throws Exception {
        //GIVEN
        CorsAllowlistFilter filter = new CorsAllowlistFilter(WEBAPP);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/endpoint-core-service/api/jobs");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        //WHEN
        filter.doFilter(request, response, chain);

        //THEN
        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isNull();
    }

    @Test
    void should_addCorsHeadersAndContinue_when_originIsAllowed() throws Exception {
        //GIVEN
        CorsAllowlistFilter filter = new CorsAllowlistFilter(WEBAPP);
        MockHttpServletRequest request = request("POST", WEBAPP);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        //WHEN
        filter.doFilter(request, response, chain);

        //THEN
        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isEqualTo(WEBAPP);
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS)).isEqualTo("true");
        assertThat(response.getHeader(HttpHeaders.VARY)).isEqualTo(HttpHeaders.ORIGIN);
    }

    @Test
    void should_continueWithoutCorsHeaders_when_originIsNotAllowed() throws Exception {
        //GIVEN
        CorsAllowlistFilter filter = new CorsAllowlistFilter(WEBAPP);
        MockHttpServletRequest request = request("POST", FOREIGN);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        //WHEN
        filter.doFilter(request, response, chain);

        //THEN
        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isNull();
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS)).isNull();
    }

    @Test
    void should_answerPreflightWithoutCallingChain_when_originIsAllowed() throws Exception {
        //GIVEN
        CorsAllowlistFilter filter = new CorsAllowlistFilter(WEBAPP);
        MockHttpServletRequest request = preflight(WEBAPP);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        //WHEN
        filter.doFilter(request, response, chain);

        //THEN
        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isEqualTo(WEBAPP);
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS)).contains("PATCH", "DELETE");
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS)).contains("Authorization");
    }

    @Test
    void should_rejectPreflightWithoutCallingChain_when_originIsNotAllowed() throws Exception {
        //GIVEN
        CorsAllowlistFilter filter = new CorsAllowlistFilter(WEBAPP);
        MockHttpServletRequest request = preflight(FOREIGN);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        //WHEN
        filter.doFilter(request, response, chain);

        //THEN
        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isNull();
    }

    @Test
    void should_allowNoOrigin_when_allowlistIsEmpty() throws Exception {
        //GIVEN
        CorsAllowlistFilter filter = new CorsAllowlistFilter("");
        MockHttpServletResponse actualResponse = new MockHttpServletResponse();
        MockHttpServletResponse preflightResponse = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        //WHEN
        filter.doFilter(request("POST", WEBAPP), actualResponse, chain);
        filter.doFilter(preflight(WEBAPP), preflightResponse, new MockFilterChain());

        //THEN
        assertThat(chain.getRequest()).isNotNull();
        assertThat(actualResponse.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isNull();
        assertThat(preflightResponse.getStatus()).isEqualTo(403);
    }

    @Test
    void should_allowEachListedOrigin_when_allowlistHasManyWithSpacesAndTrailingSlash() throws Exception {
        //GIVEN
        CorsAllowlistFilter filter = new CorsAllowlistFilter(" http://localhost:5173 , " + WEBAPP + "/ ,");
        MockHttpServletResponse first = new MockHttpServletResponse();
        MockHttpServletResponse second = new MockHttpServletResponse();
        MockHttpServletResponse foreign = new MockHttpServletResponse();

        //WHEN
        filter.doFilter(request("GET", "http://localhost:5173"), first, new MockFilterChain());
        filter.doFilter(request("GET", WEBAPP), second, new MockFilterChain());
        filter.doFilter(request("GET", FOREIGN), foreign, new MockFilterChain());

        //THEN
        assertThat(first.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isEqualTo("http://localhost:5173");
        assertThat(second.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isEqualTo(WEBAPP);
        assertThat(foreign.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isNull();
    }

    @Test
    void should_continueChain_when_optionsRequestIsNotPreflight() throws Exception {
        //GIVEN
        CorsAllowlistFilter filter = new CorsAllowlistFilter(WEBAPP);
        MockHttpServletRequest request = request("OPTIONS", WEBAPP);
        MockFilterChain chain = new MockFilterChain();

        //WHEN
        filter.doFilter(request, new MockHttpServletResponse(), chain);

        //THEN
        assertThat(chain.getRequest()).isNotNull();
    }

    private MockHttpServletRequest request(String method, String origin) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, "/endpoint-core-service/api/jobs");
        request.addHeader(HttpHeaders.ORIGIN, origin);
        return request;
    }

    private MockHttpServletRequest preflight(String origin) {
        MockHttpServletRequest request = request("OPTIONS", origin);
        request.addHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST");
        return request;
    }
}
