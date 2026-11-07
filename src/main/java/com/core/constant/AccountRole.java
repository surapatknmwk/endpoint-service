package com.core.constant;

/**
 * System roles ({@code roles.role_name}) the core service makes decisions on. Every role other than
 * {@link #DRIVER} is an owner account (creates orders and jobs).
 */
public final class AccountRole {

    // บัญชีคนส่ง — ไม่มีเมนูออเดอร์ ทำได้เฉพาะงานที่ถูก assign
    public static final String DRIVER = "DRIVER";

    private AccountRole() {
    }
}
