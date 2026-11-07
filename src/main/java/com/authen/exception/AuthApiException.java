package com.authen.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * An authen-module error with its own HTTP status. The message is shown to the user, so it must
 * be user-safe Thai text. {@code field} (optional) puts the message under that form field.
 */
@Getter
public class AuthApiException extends RuntimeException {

    private final HttpStatus status;
    private final String field;

    public AuthApiException(HttpStatus status, String message) {
        this(status, message, null);
    }

    public AuthApiException(HttpStatus status, String message, String field) {
        super(message);
        this.status = status;
        this.field = field;
    }
}
