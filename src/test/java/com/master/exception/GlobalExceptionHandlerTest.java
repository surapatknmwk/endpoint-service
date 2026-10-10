package com.master.exception;

import com.common.dto.ApiResponse;
import com.common.exception.InvalidRequestBodyException;
import com.common.exception.InvalidTokenException;
import com.common.exception.TokenExpiredException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpHeaders;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers {@link GlobalExceptionHandler} (master module): every exception type maps to its
 * documented HTTP status and carries the exception's message through.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void should_return401_when_tokenExpired() {
        //GIVEN
        TokenExpiredException ex = new TokenExpiredException("Token is invalid or expired");

        //WHEN
        ResponseEntity<ApiResponse<Object>> response = handler.handleTokenExpiredException(ex);

        //THEN
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().getMessage()).isEqualTo("Token is invalid or expired");
    }

    @Test
    void should_return401_when_tokenInvalid() {
        //GIVEN
        InvalidTokenException ex = new InvalidTokenException("Missing or invalid Authorization header");

        //WHEN
        ResponseEntity<ApiResponse<Object>> response = handler.handleInvalidTokenException(ex);

        //THEN
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().getMessage()).isEqualTo("Missing or invalid Authorization header");
    }

    @Test
    void should_return400_when_requestBodyInvalid() {
        //GIVEN
        InvalidRequestBodyException ex = new InvalidRequestBodyException("กรุณาระบุอำเภอต้นทาง");

        //WHEN
        ResponseEntity<ApiResponse<Object>> response = handler.handleInvalidRequestBodyException(ex);

        //THEN
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).isEqualTo("กรุณาระบุอำเภอต้นทาง");
    }

    @Test
    void should_return404_when_resourceNotFound() {
        //GIVEN
        ResourceNotFoundException ex = new ResourceNotFoundException("Province", "id", 99L);

        //WHEN
        ResponseEntity<ApiResponse<Object>> response = handler.handleResourceNotFoundException(ex);

        //THEN
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getMessage()).isEqualTo("Province not found with id: '99'");
    }

    @Test
    void should_return502_when_externalApiCallFails() {
        //GIVEN
        ExternalApiException ex = new ExternalApiException("Google Routes API key not configured");

        //WHEN
        ResponseEntity<ApiResponse<Object>> response = handler.handleExternalApiException(ex);

        //THEN
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody().getMessage()).isEqualTo("Google Routes API key not configured");
    }

    @Test
    void should_return429_when_googleRoutesQuotaExceeded() {
        //GIVEN
        GoogleRoutesQuotaExceededException ex = new GoogleRoutesQuotaExceededException("Daily Google Routes quota exceeded");

        //WHEN
        ResponseEntity<ApiResponse<Object>> response = handler.handleGoogleRoutesQuotaExceededException(ex);

        //THEN
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getBody().getMessage()).isEqualTo("Daily Google Routes quota exceeded");
    }

    @Test
    void should_return400_when_argumentIsIllegal() {
        //GIVEN
        IllegalArgumentException ex = new IllegalArgumentException("ระยะทางต้องมากกว่า 0 กิโลเมตร");

        //WHEN
        ResponseEntity<ApiResponse<Object>> response = handler.handleIllegalArgumentException(ex);

        //THEN
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).isEqualTo("ระยะทางต้องมากกว่า 0 กิโลเมตร");
    }

    @Test
    void should_return400WithFieldErrorMap_when_beanValidationFails() throws Exception {
        //GIVEN
        BindException bindException = new BindException(new Object(), "request");
        bindException.addError(new FieldError("request", "code", "must not be blank"));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(
                new org.springframework.core.MethodParameter(
                        GlobalExceptionHandlerTest.class.getDeclaredMethod("dummy", String.class), 0),
                bindException.getBindingResult());

        //WHEN
        ResponseEntity<ApiResponse<java.util.Map<String, String>>> response = handler.handleValidationExceptions(ex);

        //THEN
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).isEqualTo("Validation failed");
        assertThat(response.getBody().getData()).containsEntry("code", "must not be blank");
    }

    @Test
    void should_return400WithoutDetails_when_jsonBodyIsMalformed() {
        //GIVEN
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException("JSON parse error", new java.io.IOException("bad input"), null);

        //WHEN
        ResponseEntity<ApiResponse<Object>> response = handler.handleHttpMessageNotReadableException(ex);

        //THEN
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).isEqualTo("Malformed JSON request");
    }

    @Test
    void should_return405WithMethodName_when_methodNotSupported() {
        //GIVEN
        org.springframework.web.HttpRequestMethodNotSupportedException ex =
                new org.springframework.web.HttpRequestMethodNotSupportedException("DELETE");

        //WHEN
        ResponseEntity<ApiResponse<Object>> response = handler.handleMethodNotSupportedException(ex);

        //THEN
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getBody().getMessage()).isEqualTo("Method 'DELETE' is not supported");
    }

    @Test
    void should_return404WithRequestedUrl_when_noHandlerFound() {
        //GIVEN
        NoHandlerFoundException ex = new NoHandlerFoundException("GET", "/no/such/path", new HttpHeaders());

        //WHEN
        ResponseEntity<ApiResponse<Object>> response = handler.handleNoHandlerFoundException(ex);

        //THEN
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getMessage()).isEqualTo("Endpoint not found: /no/such/path");
    }

    @Test
    void should_return500WithGenericMessage_when_exceptionIsUnmapped() {
        //GIVEN - never leak internal exception details (e.g. a DB error message) to the client
        RuntimeException ex = new RuntimeException("Connection refused at db-host:5432");

        //WHEN
        ResponseEntity<ApiResponse<Object>> response = handler.handleGenericException(ex);

        //THEN
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getMessage()).isEqualTo("An unexpected error occurred");
    }

    private void dummy(String code) {
    }
}
