package com.core.exception;

import com.common.dto.ApiResponse;
import com.common.exception.InvalidRequestBodyException;
import com.common.exception.InvalidTokenException;
import com.common.exception.TokenExpiredException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers {@link GlobalExceptionHandler} (core module): every exception type maps to its
 * documented HTTP status, with the {@code ApiResponse.status} field also set to match.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void should_return401_when_tokenExpired() {
        //GIVEN
        TokenExpiredException ex = new TokenExpiredException("Token is invalid or expired");

        //WHEN
        ResponseEntity<ApiResponse<Void>> response = handler.handleTokenExpiredException(ex);

        //THEN
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().getStatus()).isEqualTo(401);
        assertThat(response.getBody().getMessage()).isEqualTo("Token is invalid or expired");
    }

    @Test
    void should_return401_when_tokenInvalid() {
        //GIVEN
        InvalidTokenException ex = new InvalidTokenException("Access denied.");

        //WHEN
        ResponseEntity<ApiResponse<Void>> response = handler.handleInvalidTokenException(ex);

        //THEN
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().getMessage()).isEqualTo("Access denied.");
    }

    @Test
    void should_return400WithFieldErrors_when_requestBodyInvalid() {
        //GIVEN
        List<ApiResponse.FieldError> errors = List.of(
                ApiResponse.FieldError.builder().field("orderCode").message("required").build());
        InvalidRequestBodyException ex = new InvalidRequestBodyException("Invalid request", errors);

        //WHEN
        ResponseEntity<ApiResponse<Void>> response = handler.handleInvalidRequestBodyException(ex);

        //THEN
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getErrors()).isSameAs(errors);
    }

    @Test
    void should_return400_when_argumentIsIllegal() {
        //GIVEN
        IllegalArgumentException ex = new IllegalArgumentException("Order not found: 1");

        //WHEN
        ResponseEntity<ApiResponse<Void>> response = handler.handleIllegalArgumentException(ex);

        //THEN
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).isEqualTo("Order not found: 1");
    }

    @Test
    void should_return400WithFieldErrorList_when_beanValidationFails() throws Exception {
        //GIVEN
        BindException bindException = new BindException(new Object(), "request");
        bindException.addError(new FieldError("request", "customer", "must not be null"));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(
                new org.springframework.core.MethodParameter(
                        GlobalExceptionHandlerTest.class.getDeclaredMethod("dummy", String.class), 0),
                bindException.getBindingResult());

        //WHEN
        ResponseEntity<ApiResponse<Void>> response = handler.handleValidationException(ex);

        //THEN
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).isEqualTo("Validation failed");
        assertThat(response.getBody().getErrors()).extracting(ApiResponse.FieldError::getField).containsExactly("customer");
    }

    @Test
    void should_return400_when_jsonBodyIsMalformed() {
        //GIVEN
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException(
                "JSON parse error", new java.io.IOException("bad input"), null);

        //WHEN
        ResponseEntity<ApiResponse<Void>> response = handler.handleHttpMessageNotReadable(ex);

        //THEN
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).isEqualTo("Malformed JSON request");
    }

    @Test
    void should_return405_when_methodNotSupported() {
        //GIVEN
        HttpRequestMethodNotSupportedException ex = new HttpRequestMethodNotSupportedException("PATCH");

        //WHEN
        ResponseEntity<ApiResponse<Void>> response = handler.handleMethodNotSupported(ex);

        //THEN
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
    }

    @Test
    void should_return404_when_noHandlerFound() {
        //GIVEN
        NoHandlerFoundException ex = new NoHandlerFoundException("GET", "/no/such/path", new HttpHeaders());

        //WHEN
        ResponseEntity<ApiResponse<Void>> response = handler.handleNoHandlerFound(ex);

        //THEN
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getMessage()).isEqualTo("Resource not found");
    }

    @Test
    void should_return500WithGenericMessage_when_exceptionIsUnmapped() {
        //GIVEN - never leak internal exception details to the client
        RuntimeException ex = new RuntimeException("Connection refused at db-host:5432");

        //WHEN
        ResponseEntity<ApiResponse<Void>> response = handler.handleGenericException(ex);

        //THEN
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getMessage()).isEqualTo("An unexpected error occurred");
    }

    private void dummy(String code) {
    }
}
