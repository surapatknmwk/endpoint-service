package com.common.exception;

/**
 * The token is valid but its role may not call this endpoint. Answered with {@code 403} so the
 * webapp can tell it apart from an invalid or expired token ({@code 401}, which logs out).
 */
public class PermissionDeniedException extends RuntimeException {

    public PermissionDeniedException(String message) {
        super(message);
    }
}
