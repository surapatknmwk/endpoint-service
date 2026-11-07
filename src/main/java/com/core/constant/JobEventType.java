package com.core.constant;

/** Values of {@code delivery_job_events.event_type} — one per action on a job (audit log, driver-risk round 2). */
public final class JobEventType {

    // ทั้งงาน (order_id = null)
    public static final String JOB_STARTED = "JOB_STARTED";
    public static final String JOB_EDITED = "JOB_EDITED";
    // ปิดงานเอง (จุดที่ค้างกลายเป็นข้าม)
    public static final String JOB_CLOSED = "JOB_CLOSED";
    // ปิดอัตโนมัติ — ทุกจุดส่งแล้ว / ยกเลิกแล้ว
    public static final String JOB_FINISHED = "JOB_FINISHED";
    // คนส่งคืนงานก่อนเริ่ม (driver-risk ข้อ 16) — reason = DeclineReason
    public static final String JOB_RELEASED = "JOB_RELEASED";

    // รายจุด
    public static final String STOP_COMPLETED = "STOP_COMPLETED";
    public static final String STOP_UNDONE = "STOP_UNDONE";
    public static final String STOP_SKIPPED = "STOP_SKIPPED";
    public static final String STOP_CANCELLED = "STOP_CANCELLED";
    public static final String STOP_CANCEL_REQUESTED = "STOP_CANCEL_REQUESTED";
    public static final String STOP_CANCEL_APPROVED = "STOP_CANCEL_APPROVED";
    public static final String STOP_CANCEL_REJECTED = "STOP_CANCEL_REJECTED";

    private JobEventType() {
    }
}
