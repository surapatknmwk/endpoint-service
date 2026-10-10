package com.core.constant;

/**
 * Values of {@code delivery_jobs.delivery_status}. Open = anything but {@link #COMPLETED}.
 */
public final class JobStatus {

    // สร้างแล้ว ยังไม่ออกรถ (รอออกรถ) — ค่าเริ่มต้นของงาน
    public static final String NEW = "NEW";
    // กด "เริ่มงาน" แล้ว (หรือเริ่มอัตโนมัติเมื่อกดส่ง/ข้าม/ยกเลิกจุดแรก)
    public static final String IN_PROGRESS = "IN_PROGRESS";
    public static final String COMPLETED = "COMPLETED";

    private JobStatus() {
    }
}
