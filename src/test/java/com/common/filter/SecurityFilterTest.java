package com.common.filter;

import com.authen.constant.SecurityEventType;
import com.authen.service.AuthenticationService;
import com.authen.service.SecurityEventRecorder;
import com.core.service.security.SecurityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Covers {@link SecurityFilter}: token validation and the role permission check on every
 * module prefix (core / master / search).
 */
@ExtendWith(MockitoExtension.class)
class SecurityFilterTest {

    private static final String TOKEN = "header."
            + Base64.getUrlEncoder().withoutPadding().encodeToString("{\"sub\":\"admin\"}".getBytes(StandardCharsets.UTF_8))
            + ".signature";
    private static final String AUTH_HEADER = "Bearer " + TOKEN;

    private static final String CORE_PATTERN = "/endpoint-core-service/api/jobs/{jobId}";
    private static final String MASTER_PATTERN = "/endpoint-master-service/api/provinces/{id}";
    private static final String SEARCH_PATTERN = "/endpoint-search-service/api/search/orders";

    @Mock
    private AuthenticationService authenticationService;
    @Mock
    private SecurityService securityService;
    @Mock
    private RequestMappingHandlerMapping requestMappingHandlerMapping;
    @Mock
    private SecurityEventRecorder securityEventRecorder;

    private SecurityFilter filter;

    @BeforeEach
    void setUp() {
        filter = new SecurityFilter(authenticationService, securityService, requestMappingHandlerMapping,
                securityEventRecorder);
        ReflectionTestUtils.setField(filter, "filterEnabled", true);
        ReflectionTestUtils.setField(filter, "tokenHeader", "Authorization");
        ReflectionTestUtils.setField(filter, "tokenPrefix", "Bearer");
    }

    @Test
    void should_continueChain_when_corePermissionGranted() throws Exception {
        //GIVEN
        MockHttpServletRequest request = request("PUT", "/endpoint-core-service/api/jobs/7", CORE_PATTERN, AUTH_HEADER);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        when(authenticationService.validateToken(TOKEN)).thenReturn(true);
        when(securityService.checkPermission(AUTH_HEADER, "PUT", CORE_PATTERN)).thenReturn(null);

        //WHEN
        filter.doFilter(request, response, chain);

        //THEN
        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void should_checkPermission_when_requestIsMasterPath() throws Exception {
        //GIVEN
        MockHttpServletRequest request = request("GET", "/endpoint-master-service/api/provinces/1", MASTER_PATTERN, AUTH_HEADER);
        MockFilterChain chain = new MockFilterChain();
        when(authenticationService.validateToken(TOKEN)).thenReturn(true);
        when(securityService.checkPermission(AUTH_HEADER, "GET", MASTER_PATTERN)).thenReturn(null);

        //WHEN
        filter.doFilter(request, new MockHttpServletResponse(), chain);

        //THEN
        verify(securityService).checkPermission(AUTH_HEADER, "GET", MASTER_PATTERN);
        assertThat(chain.getRequest()).isNotNull();
        verifyNoInteractions(securityEventRecorder);
    }

    @Test
    void should_respond401WithoutCallingChain_when_masterPermissionDenied() throws Exception {
        //GIVEN
        MockHttpServletRequest request = request("GET", "/endpoint-master-service/api/provinces/1", MASTER_PATTERN, AUTH_HEADER);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        when(authenticationService.validateToken(TOKEN)).thenReturn(true);
        when(securityService.checkPermission(AUTH_HEADER, "GET", MASTER_PATTERN)).thenReturn("Access denied.");
        when(securityService.extractUsernameFromToken(AUTH_HEADER)).thenReturn("admin");

        //WHEN
        filter.doFilter(request, response, chain);

        //THEN
        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("Access denied.");
        verify(securityEventRecorder).record(SecurityEventType.PERMISSION_DENIED, request, "admin", "Access denied.");
    }

    @Test
    void should_checkPermissionAndSetCurrentUserId_when_requestIsSearchPath() throws Exception {
        //GIVEN
        MockHttpServletRequest request = request("POST", "/endpoint-search-service/api/search/orders", SEARCH_PATTERN, AUTH_HEADER);
        MockFilterChain chain = new MockFilterChain();
        when(authenticationService.validateToken(TOKEN)).thenReturn(true);
        when(securityService.checkPermission(AUTH_HEADER, "POST", SEARCH_PATTERN)).thenReturn(null);

        //WHEN
        filter.doFilter(request, new MockHttpServletResponse(), chain);

        //THEN
        verify(securityService).checkPermission(AUTH_HEADER, "POST", SEARCH_PATTERN);
        assertThat(request.getAttribute("currentUserId")).isEqualTo("admin");
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void should_respond401WithoutCallingChain_when_searchPermissionDenied() throws Exception {
        //GIVEN
        MockHttpServletRequest request = request("POST", "/endpoint-search-service/api/search/orders", SEARCH_PATTERN, AUTH_HEADER);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        when(authenticationService.validateToken(TOKEN)).thenReturn(true);
        when(securityService.checkPermission(AUTH_HEADER, "POST", SEARCH_PATTERN)).thenReturn("Access denied.");

        //WHEN
        filter.doFilter(request, response, chain);

        //THEN
        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(request.getAttribute("currentUserId")).isNull();
    }

    @Test
    void should_respond401WithoutCheckingPermission_when_authorizationHeaderMissing() throws Exception {
        //GIVEN
        MockHttpServletRequest request = request("GET", "/endpoint-master-service/api/provinces/1", MASTER_PATTERN, null);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        //WHEN
        filter.doFilter(request, response, chain);

        //THEN
        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(authenticationService, securityService, securityEventRecorder);
    }

    @Test
    void should_respond401WithoutCheckingPermission_when_tokenIsInvalidOrExpired() throws Exception {
        //GIVEN
        MockHttpServletRequest request = request("GET", "/endpoint-master-service/api/provinces/1", MASTER_PATTERN, AUTH_HEADER);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        when(authenticationService.validateToken(TOKEN)).thenReturn(false);

        //WHEN
        filter.doFilter(request, response, chain);

        //THEN
        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(securityService);
    }

    @Test
    void should_continueWithoutAnyCheck_when_filterIsDisabled() throws Exception {
        //GIVEN
        ReflectionTestUtils.setField(filter, "filterEnabled", false);
        MockHttpServletRequest request = request("GET", "/endpoint-master-service/api/provinces/1", MASTER_PATTERN, null);
        MockFilterChain chain = new MockFilterChain();

        //WHEN
        filter.doFilter(request, new MockHttpServletResponse(), chain);

        //THEN
        assertThat(chain.getRequest()).isNotNull();
        verifyNoInteractions(authenticationService, securityService);
    }

    @Test
    void should_continueWithoutAnyCheck_when_pathIsAuthenModule() throws Exception {
        //GIVEN
        MockHttpServletRequest request = request("POST", "/endpoint-authen-service/auth/login", null, null);
        MockFilterChain chain = new MockFilterChain();

        //WHEN
        filter.doFilter(request, new MockHttpServletResponse(), chain);

        //THEN
        assertThat(chain.getRequest()).isNotNull();
        verifyNoInteractions(authenticationService, securityService);
    }

    @Test
    void should_notWriteCorsHeaders_when_requestCarriesOrigin() throws Exception {
        //GIVEN
        MockHttpServletRequest request = request("GET", "/endpoint-master-service/api/provinces/1", MASTER_PATTERN, AUTH_HEADER);
        request.addHeader(HttpHeaders.ORIGIN, "https://evil.example.net");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(authenticationService.validateToken(TOKEN)).thenReturn(true);
        when(securityService.checkPermission(AUTH_HEADER, "GET", MASTER_PATTERN)).thenReturn(null);

        //WHEN
        filter.doFilter(request, response, new MockFilterChain());

        //THEN
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isNull();
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS)).isNull();
    }

    private MockHttpServletRequest request(String method, String uri, String matchedPattern, String authHeader) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        if (matchedPattern != null) {
            request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, matchedPattern);
        }
        if (authHeader != null) {
            request.addHeader("Authorization", authHeader);
        }
        return request;
    }
}
