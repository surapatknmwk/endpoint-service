package com.authen.controller;

import com.authen.dto.CheckPermissionRequest;
import com.authen.dto.LoginRequest;
import com.authen.dto.LoginResponse;
import com.authen.service.AuthenticationService;
import com.common.dto.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers {@link AuthenticationController}: the bearer prefix is stripped before reaching the
 * service, and every service failure becomes a {@code 400} with the error message.
 */
@ExtendWith(MockitoExtension.class)
class AuthenticationControllerTest {

    @Mock
    private AuthenticationService authenticationService;

    private AuthenticationController controller;
    private final MockHttpServletRequest httpRequest = new MockHttpServletRequest();

    @BeforeEach
    void setUp() {
        controller = new AuthenticationController(authenticationService);
    }

    // ---------- login ----------

    @Test
    void should_return200WithToken_when_loginSucceeds() {
        //GIVEN
        LoginRequest request = new LoginRequest("admin", "S3cret!pass");
        when(authenticationService.login(request, httpRequest))
                .thenReturn(LoginResponse.builder().token("jwt-token").build());

        //WHEN
        ResponseEntity<ApiResponse<LoginResponse>> response = controller.login(request, httpRequest);

        //THEN
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData().getToken()).isEqualTo("jwt-token");
    }

    @Test
    void should_return400WithReason_when_loginFails() {
        //GIVEN
        LoginRequest request = new LoginRequest("admin", "wrong");
        when(authenticationService.login(request, httpRequest))
                .thenThrow(new RuntimeException("Invalid username or password"));

        //WHEN
        ResponseEntity<ApiResponse<LoginResponse>> response = controller.login(request, httpRequest);

        //THEN
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getMessage()).isEqualTo("Invalid username or password");
        assertThat(response.getBody().getData()).isNull();
    }

    // ---------- logout ----------

    @Test
    void should_logoutWithBareToken_when_headerHasBearerPrefix() {
        //GIVEN
        String authHeader = "Bearer jwt-token";

        //WHEN
        ResponseEntity<ApiResponse<Void>> response = controller.logout(authHeader);

        //THEN
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(authenticationService).logout("jwt-token");
    }

    @Test
    void should_return400_when_logoutFails() {
        //GIVEN
        doThrow(new RuntimeException("DB unreachable")).when(authenticationService).logout("jwt-token");

        //WHEN
        ResponseEntity<ApiResponse<Void>> response = controller.logout("Bearer jwt-token");

        //THEN
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).isEqualTo("DB unreachable");
    }

    // ---------- validate ----------

    @Test
    void should_return200True_when_tokenIsValid() {
        //GIVEN
        when(authenticationService.validateToken("jwt-token")).thenReturn(true);

        //WHEN
        ResponseEntity<ApiResponse<Boolean>> response = controller.validateToken("Bearer jwt-token");

        //THEN
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData()).isTrue();
    }

    @Test
    void should_return400_when_tokenIsNotValid() {
        //GIVEN
        when(authenticationService.validateToken("jwt-token")).thenReturn(false);

        //WHEN
        ResponseEntity<ApiResponse<Boolean>> response = controller.validateToken("Bearer jwt-token");

        //THEN
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getMessage()).isEqualTo("Token Validation Failed");
    }

    // ---------- check-permission ----------

    @Test
    void should_return200WithGrantedMessage_when_roleHasPermission() {
        //GIVEN
        when(authenticationService.checkPermission("jwt-token", "GET", "/api/jobs")).thenReturn(true);

        //WHEN
        ResponseEntity<ApiResponse<Boolean>> response =
                controller.checkPermission("Bearer jwt-token", new CheckPermissionRequest("GET", "/api/jobs"));

        //THEN
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData()).isTrue();
        assertThat(response.getBody().getMessage()).isEqualTo("Permission granted");
    }

    @Test
    void should_return200WithDeniedMessage_when_roleHasNoPermission() {
        //GIVEN
        when(authenticationService.checkPermission("jwt-token", "DELETE", "/api/jobs/{jobId}")).thenReturn(false);

        //WHEN
        ResponseEntity<ApiResponse<Boolean>> response =
                controller.checkPermission("Bearer jwt-token", new CheckPermissionRequest("DELETE", "/api/jobs/{jobId}"));

        //THEN
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData()).isFalse();
        assertThat(response.getBody().getMessage()).isEqualTo("Permission denied");
    }

    @Test
    void should_return400_when_permissionCheckIsForInvalidToken() {
        //GIVEN
        when(authenticationService.checkPermission("bad", "GET", "/api/jobs"))
                .thenThrow(new RuntimeException("Invalid or expired token"));

        //WHEN
        ResponseEntity<ApiResponse<Boolean>> response =
                controller.checkPermission("Bearer bad", new CheckPermissionRequest("GET", "/api/jobs"));

        //THEN
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).isEqualTo("Invalid or expired token");
    }

    // ---------- refresh ----------

    @Test
    void should_return200WithNewToken_when_refreshSucceeds() {
        //GIVEN
        when(authenticationService.refreshToken("old-token", httpRequest))
                .thenReturn(LoginResponse.builder().token("new-token").build());

        //WHEN
        ResponseEntity<ApiResponse<LoginResponse>> response = controller.refreshToken("Bearer old-token", httpRequest);

        //THEN
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData().getToken()).isEqualTo("new-token");
    }

    @Test
    void should_return400_when_refreshingExpiredToken() {
        //GIVEN
        when(authenticationService.refreshToken("old-token", httpRequest))
                .thenThrow(new RuntimeException("Token has expired"));

        //WHEN
        ResponseEntity<ApiResponse<LoginResponse>> response = controller.refreshToken("Bearer old-token", httpRequest);

        //THEN
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).isEqualTo("Token has expired");
    }
}
