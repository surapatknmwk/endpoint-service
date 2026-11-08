package com.core.constant;

/**
 * System roles ({@code roles.role_name}) the core service makes decisions on. Every role other than
 * {@link #DRIVER} is an owner account (creates orders and jobs).
 */
public final class AccountRole {

    // บัญชีคนส่ง — ไม่มีเมนูออเดอร์ ทำได้เฉพาะงานที่ถูก assign
    public static final String DRIVER = "DRIVER";

    // ผู้ดูแลระบบ — เห็นเฉพาะเมนูผู้ดูแลระบบ (admin.md) · สิทธิ์ endpoint: db/permissions-admin-data.sql
    public static final String ADMIN = "ADMIN";

    private AccountRole() {
    }
}
