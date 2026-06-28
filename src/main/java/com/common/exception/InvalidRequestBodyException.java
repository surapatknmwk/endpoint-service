package com.common.exception;

import com.common.dto.ApiResponse;
import lombok.Getter;

import java.util.List;

@Getter
public class InvalidRequestBodyException extends RuntimeException {

    private final List<ApiResponse.FieldError> errors;

    public InvalidRequestBodyException(String message) {
        super(message);
        this.errors = null;
    }

    public InvalidRequestBodyException(String message, List<ApiResponse.FieldError> errors) {
        super(message);
        this.errors = errors;
    }
}
