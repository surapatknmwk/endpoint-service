package com.core.constant;

/**
 * Values of {@code orders.order_status}. Must match the CHECK constraint in
 * {@code db/schema-db.sql} and {@code ORDER_STATUS} in the webapp.
 */
public final class OrderStatus {

    public static final String NEW = "NEW";
    public static final String PENDING = "PENDING";
    // อยู่ในงานที่ยังไม่ออกรถ (งาน NEW) — เป็น IN_TRANSIT เมื่องานเริ่ม
    public static final String ASSIGNED = "ASSIGNED";
    public static final String IN_TRANSIT = "IN_TRANSIT";
    public static final String COMPLETED = "COMPLETED";
    public static final String CANCELLED = "CANCELLED";
    // ถูกข้ามครบจำนวนครั้งที่กำหนด — ส่งไม่สำเร็จ ไม่ถูกแนะนำเข้างานอีก (ส่งใหม่ = คัดลอกเป็นออเดอร์ใหม่)
    public static final String FAILED = "FAILED";

    private OrderStatus() {
    }
}
