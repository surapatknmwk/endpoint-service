package com.core.constant;

/**
 * Values of {@code job_driver_requests.request_status} (design assignConfirm §4). Only {@link #PENDING} is open;
 * the others are final and kept as history (asking the same driver again creates a new row).
 */
public final class JobDriverRequestStatus {

    // เจ้าของส่งคำขอแล้ว คนส่งยังไม่ตอบ — มีได้ทีละ 1 ต่องาน
    public static final String PENDING = "PENDING";
    // คนส่งรับงาน → delivery_jobs.assigned_driver_id
    public static final String ACCEPTED = "ACCEPTED";
    // คนส่งปฏิเสธ (เหตุผลไม่บังคับ)
    public static final String DECLINED = "DECLINED";
    // เจ้าของเปลี่ยนคน / ปลด / เริ่มงานเอง / ลบงาน หรือคนส่งออกจากทีม / ถูกนำออก
    public static final String CANCELLED = "CANCELLED";
    // คนส่งไม่ตอบภายในเวลา (JobRequestPolicy) — ระบบเปลี่ยนให้ แจ้งเจ้าของ (driver-risk ข้อ 16)
    public static final String EXPIRED = "EXPIRED";
    // คนส่งรับแล้ว แต่คืนงานก่อนเริ่ม ("คืนงาน" — เหตุผลไม่บังคับ ใช้ DeclineReason เดียวกับการปฏิเสธ)
    public static final String RELEASED = "RELEASED";

    private JobDriverRequestStatus() {
    }
}
