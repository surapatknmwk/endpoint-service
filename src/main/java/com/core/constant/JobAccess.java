package com.core.constant;

/**
 * Values of {@code JobResponse.myAccess}: what the caller may do on a job. The webapp shows buttons from this,
 * not from the account type.
 */
public final class JobAccess {

    // เจ้าของงาน — ทำได้ทุกอย่าง (แก้ ลบ เลือกคนส่ง และทุกอย่างของ OPERATE)
    public static final String MANAGE = "MANAGE";
    // คนส่งที่ถูก assign และยังเป็นสมาชิก ACTIVE — เริ่มงาน ส่ง ข้าม ยกเลิกจุด ย้อนกลับ ปิดงาน
    public static final String OPERATE = "OPERATE";

    private JobAccess() {
    }
}
