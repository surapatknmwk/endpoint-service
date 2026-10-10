package com.core.service.security;

import com.authen.service.AuthenticationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Covers {@link SecurityService}: reading username/role out of the bearer token and turning the
 * permission lookup into the message the filter sends back (null = allowed).
 */
@ExtendWith(MockitoExtension.class)
class SecurityServiceTest {

    @Mock
    private AuthenticationService authenticationService;

    private SecurityService securityService;

    @BeforeEach
    void setUp() {
        securityService = new SecurityService(authenticationService);
        ReflectionTestUtils.setField(securityService, "tokenPrefix", "Bearer ");
    }

    private static String token(String payloadJson) {
        return "header." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8)) + ".signature";
    }

    private static String bearer(String payloadJson) {
        return "Bearer " + token(payloadJson);
    }

    // ---------- extractUsernameFromToken / extractRoleFromToken ----------

    @Test
    void should_returnSubAndRole_when_tokenPayloadHasThem() {
        //GIVEN
        String authHeader = bearer("{\"sub\":\"สมชาย\",\"role\":\"USER_VIP_0\"}");

        //WHEN
        String username = securityService.extractUsernameFromToken(authHeader);
        String role = securityService.extractRoleFromToken(authHeader);

        //THEN
        assertThat(username).isEqualTo("สมชาย");
        assertThat(role).isEqualTo("USER_VIP_0");
    }

    @Test
    void should_returnNull_when_tokenPayloadHasNoSubOrRole() {
        //GIVEN
        String authHeader = bearer("{\"userId\":7}");

        //WHEN
        String username = securityService.extractUsernameFromToken(authHeader);
        String role = securityService.extractRoleFromToken(authHeader);

        //THEN
        assertThat(username).isNull();
        assertThat(role).isNull();
    }

    @Test
    void should_returnNull_when_tokenDoesNotHaveThreeParts() {
        //GIVEN
        String twoParts = "Bearer header.payload";
        String fourParts = "Bearer a.b.c.d";

        //WHEN
        String fromTwo = securityService.extractUsernameFromToken(twoParts);
        String fromFour = securityService.extractUsernameFromToken(fourParts);

        //THEN
        assertThat(fromTwo).isNull();
        assertThat(fromFour).isNull();
    }

    @Test
    void should_returnNull_when_tokenPayloadIsNotBase64Json() {
        //GIVEN
        String notBase64 = "Bearer header.***.signature";
        String notJson = "Bearer header." + Base64.getUrlEncoder().encodeToString("plain text".getBytes()) + ".signature";

        //WHEN
        String fromNotBase64 = securityService.extractRoleFromToken(notBase64);
        String fromNotJson = securityService.extractRoleFromToken(notJson);

        //THEN
        assertThat(fromNotBase64).isNull();
        assertThat(fromNotJson).isNull();
    }

    @Test
    void should_returnNull_when_authHeaderIsNullOrShorterThanPrefix() {
        //GIVEN
        String missing = null;
        String tooShort = "Bear";

        //WHEN
        String fromMissing = securityService.extractUsernameFromToken(missing);
        String fromTooShort = securityService.extractUsernameFromToken(tooShort);

        //THEN
        assertThat(fromMissing).isNull();
        assertThat(fromTooShort).isNull();
    }

    // ---------- checkPermission ----------

    @Test
    void should_returnNull_when_roleHasPermission() {
        //GIVEN
        String payload = "{\"sub\":\"admin\",\"role\":\"USER_VIP_0\"}";
        when(authenticationService.checkPermission(token(payload), "GET", "/endpoint-core-service/api/jobs"))
                .thenReturn(true);

        //WHEN
        String result = securityService.checkPermission(bearer(payload), "GET", "/endpoint-core-service/api/jobs");

        //THEN
        assertThat(result).isNull();
    }

    @Test
    void should_returnAccessDenied_when_roleHasNoPermission() {
        //GIVEN
        String payload = "{\"sub\":\"admin\",\"role\":\"USER_VIP_0\"}";
        when(authenticationService.checkPermission(token(payload), "DELETE", "/endpoint-core-service/api/jobs/{jobId}"))
                .thenReturn(false);

        //WHEN
        String result = securityService.checkPermission(bearer(payload), "DELETE", "/endpoint-core-service/api/jobs/{jobId}");

        //THEN
        assertThat(result).isEqualTo("Access denied. You don't have permission to access this resource.");
    }

    @Test
    void should_denyWithoutLookingUpPermission_when_tokenHasNoRole() {
        //GIVEN
        String authHeader = bearer("{\"sub\":\"admin\"}");

        //WHEN
        String result = securityService.checkPermission(authHeader, "GET", "/endpoint-core-service/api/jobs");

        //THEN
        assertThat(result).isEqualTo("Unable to extract role from token");
        verifyNoInteractions(authenticationService);
    }

    @Test
    void should_denyWithRetryMessage_when_permissionLookupFails() {
        //GIVEN
        String payload = "{\"sub\":\"admin\",\"role\":\"USER_VIP_0\"}";
        when(authenticationService.checkPermission(token(payload), "GET", "/endpoint-core-service/api/jobs"))
                .thenThrow(new RuntimeException("DB unreachable"));

        //WHEN
        String result = securityService.checkPermission(bearer(payload), "GET", "/endpoint-core-service/api/jobs");

        //THEN
        assertThat(result).isEqualTo("Unable to verify permission. Please try again later.");
    }

    // ---------- checkPermissionCached ----------

    @Test
    void should_passTokenWithoutBearerPrefix_when_checkingPermission() {
        //GIVEN
        String payload = "{\"sub\":\"admin\",\"role\":\"USER_VIP_0\"}";
        when(authenticationService.checkPermission(token(payload), "GET", "/api")).thenReturn(true);

        //WHEN
        boolean result = securityService.checkPermissionCached("USER_VIP_0", "GET", "/api", bearer(payload));

        //THEN
        assertThat(result).isTrue();
        verify(authenticationService).checkPermission(token(payload), "GET", "/api");
    }

    @Test
    void should_wrapFailure_when_permissionLookupThrows() {
        //GIVEN
        String payload = "{\"sub\":\"admin\",\"role\":\"USER_VIP_0\"}";
        when(authenticationService.checkPermission(token(payload), "GET", "/api"))
                .thenThrow(new RuntimeException("Invalid or expired token"));

        //WHEN / THEN
        assertThatThrownBy(() -> securityService.checkPermissionCached("USER_VIP_0", "GET", "/api", bearer(payload)))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Permission check failed");
    }
}
