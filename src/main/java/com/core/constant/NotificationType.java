package com.core.constant;

/**
 * Values of {@code notifications.notification_type}. Free text in the database, so a new type only needs a
 * constant here plus the code that publishes it.
 */
public final class NotificationType {

    // งาน (job) ที่นัดส่งพรุ่งนี้ และยังไม่ออกรถ
    public static final String JOB_DUE_TOMORROW = "JOB_DUE_TOMORROW";

    // เจ้าของ: order ที่ยังไม่ได้จัดเข้างาน ใกล้ถึงวันนัดส่ง (≤ 7 วัน) — รวม 1 รายการต่อวัน กดแล้วไปรายการ order ที่กรองไว้
    public static final String ORDER_DUE_SOON = "ORDER_DUE_SOON";

    // ทีม (design roleAndPermission §6.6) — กดแล้วไปหน้าทีม
    // คนส่ง: ถูกเจ้าของเชิญเข้าทีม
    public static final String TEAM_INVITED = "TEAM_INVITED";
    // เจ้าของ: คนส่งตอบรับคำเชิญ
    public static final String TEAM_INVITE_ACCEPTED = "TEAM_INVITE_ACCEPTED";
    // เจ้าของ: คนส่งปฏิเสธคำเชิญ
    public static final String TEAM_INVITE_DECLINED = "TEAM_INVITE_DECLINED";
    // คนส่ง: ถูกเจ้าของนำออกจากทีม
    public static final String TEAM_REMOVED = "TEAM_REMOVED";
    // เจ้าของ: คนส่งออกจากทีมเอง
    public static final String TEAM_MEMBER_LEFT = "TEAM_MEMBER_LEFT";

    // คนส่ง: ได้รับมอบหมายงาน — กดแล้วเปิดงาน · ไม่สร้างแล้วตั้งแต่มีคำขอรับงาน (แทนด้วย JOB_REQUESTED) แต่ของเก่ายังแสดงได้
    public static final String JOB_ASSIGNED = "JOB_ASSIGNED";

    // คำขอรับงาน (design assignConfirm §5)
    // คนส่ง: เจ้าของขอให้รับงาน — กดแล้วไปรายการงาน ส่วน "รอตอบรับ"
    public static final String JOB_REQUESTED = "JOB_REQUESTED";
    // เจ้าของ: คนส่งรับงานแล้ว — กดแล้วเปิดงาน
    public static final String JOB_REQUEST_ACCEPTED = "JOB_REQUEST_ACCEPTED";
    // เจ้าของ: คนส่งปฏิเสธงาน (+ เหตุผล) — กดแล้วเปิดงาน
    public static final String JOB_REQUEST_DECLINED = "JOB_REQUEST_DECLINED";
    // คนส่ง: คำขอถูกยกเลิก (เจ้าของเปลี่ยนคน / ปลด / เริ่มงานเอง / ลบงาน) — กดแล้วไปรายการงาน
    public static final String JOB_REQUEST_CANCELLED = "JOB_REQUEST_CANCELLED";
    // คนส่ง: ถูกปลดจากงาน (เปลี่ยนคนส่ง / ปลด / ลบงาน) — กดแล้วไปหน้าทีม เพราะเปิดงานนั้นไม่ได้แล้ว
    public static final String JOB_UNASSIGNED = "JOB_UNASSIGNED";

    // แจ้งยกเลิกจุดส่ง (driver-risk รอบ 2) — กดแล้วเปิดงาน
    // เจ้าของ: คนส่งแจ้งว่าลูกค้ายกเลิก รออนุมัติ
    public static final String STOP_CANCEL_REQUESTED = "STOP_CANCEL_REQUESTED";
    // คนส่ง: เจ้าของอนุมัติ / ไม่อนุมัติการยกเลิก
    public static final String STOP_CANCEL_APPROVED = "STOP_CANCEL_APPROVED";
    public static final String STOP_CANCEL_REJECTED = "STOP_CANCEL_REJECTED";

    // driver-risk รอบ 3 (ข้อ 16) — กดแล้วเปิดงาน
    // เจ้าของ: คนส่งไม่ตอบคำขอภายในเวลา คำขอหมดอายุ
    public static final String JOB_REQUEST_EXPIRED = "JOB_REQUEST_EXPIRED";
    // เจ้าของ: คนส่งคืนงานที่รับไว้ (ก่อนเริ่มงาน)
    public static final String JOB_RELEASED = "JOB_RELEASED";
    // เจ้าของ + คนส่ง: ถึงวันนัดแล้ว (หลังเวลาที่ตั้งไว้) งานยังไม่เริ่ม
    public static final String JOB_NOT_STARTED = "JOB_NOT_STARTED";
    // เจ้าของ: คนส่งในทีมเข้าสู่ระบบจากเครื่องใหม่ (ข้อ 15) — กดแล้วไปหน้าทีม
    public static final String DRIVER_NEW_DEVICE = "DRIVER_NEW_DEVICE";

    // ทุกคน / เจ้าของ / คนส่ง: ประกาศระบบ (ปิดปรับปรุง / กรณีพิเศษ) จากตาราง system_announcements — กดแล้วเปิดดูข้อความเต็ม
    public static final String SYSTEM_ANNOUNCEMENT = "SYSTEM_ANNOUNCEMENT";

    private NotificationType() {
    }
}
