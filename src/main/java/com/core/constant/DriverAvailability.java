package com.core.constant;

import java.util.Set;

/**
 * Values of {@code user_profiles.availability_status} — set by a DRIVER account, shown to the owners of their
 * teams. Only informs; nothing is blocked by it. "กำลังส่งงาน" is computed from the jobs, never stored here.
 */
public final class DriverAvailability {

    // เปิดรับงาน — ค่าเริ่มต้น (ไม่มีแถว user_profiles = ค่านี้)
    public static final String AVAILABLE = "AVAILABLE";
    // ไม่ว่างรับงาน
    public static final String BUSY = "BUSY";
    // ปิดรับงาน
    public static final String OFF = "OFF";

    public static final Set<String> ALL = Set.of(AVAILABLE, BUSY, OFF);

    private DriverAvailability() {
    }
}
