package com.authen.exception;

import com.common.dto.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers {@link GlobalExceptionHandler} (authen module): validation errors become a field→message
 * map, and any other {@link RuntimeException} (e.g. "Invalid username or password") becomes a
 * {@code 400} with its own message, never a generic one — that is how the controller surfaces
 * login failures to the caller.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void should_return400WithFieldToMessageMap_when_beanValidationFails() throws Exception {
        //GIVEN
        BindException bindException = new BindException(new Object(), "request");
        bindException.addError(new FieldError("request", "username", "Username is required"));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(
                new org.springframework.core.MethodParameter(
                        GlobalExceptionHandlerTest.class.getDeclaredMethod("dummy", String.class), 0),
                bindException.getBindingResult());

        //WHEN
        ResponseEntity<ApiResponse<Map<String, String>>> response = handler.handleValidationExceptions(ex);

        //THEN
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).isEqualTo("Validation failed");
        assertThat(response.getBody().getData()).containsEntry("username", "Username is required");
    }

    @Test
    void should_return400WithItsOwnMessage_when_runtimeExceptionIsThrown() {
        //GIVEN - e.g. AuthenticationService.login's "Invalid username or password"
        RuntimeException ex = new RuntimeException("Invalid username or password");

        //WHEN
        ResponseEntity<ApiResponse<Void>> response = handler.handleRuntimeException(ex);

        //THEN
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getMessage()).isEqualTo("Invalid username or password");
    }

    @Test
    void should_return500WithGenericMessage_when_exceptionIsNotARuntimeException() {
        //GIVEN - e.g. a checked exception wrapped as Exception; never leak its message
        Exception ex = new Exception("Connection refused at db-host:5432");

        //WHEN
        ResponseEntity<ApiResponse<Void>> response = handler.handleGenericException(ex);

        //THEN
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getMessage()).isEqualTo("An unexpected error occurred");
    }

    private void dummy(String code) {
    }
}
