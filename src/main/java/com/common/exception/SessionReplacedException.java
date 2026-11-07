package com.common.exception;

/**
 * The token's session was ended because the same account logged in on another device
 * (one session per account). Answered as 401 with {@code data.code = SESSION_REPLACED} so the
 * webapp shows a popup before going back to the login page.
 */
public class SessionReplacedException extends RuntimeException {

    public static final String CODE = "SESSION_REPLACED";
    public static final String MESSAGE = "บัญชีนี้เข้าสู่ระบบจากเครื่องอื่นแล้ว";

    public SessionReplacedException() {
        super(MESSAGE);
    }
}
