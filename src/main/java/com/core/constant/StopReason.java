package com.core.constant;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Values of {@code delivery_routing.status_reason} and {@code delivery_job_events.reason} — why a stop was skipped
 * or cancelled (driver-risk round 2). Skip and cancel each require one of their own reasons; {@link #OTHER} needs a
 * typed note. The system sets {@link #JOB_CLOSED} / {@link #CANCEL_NOT_APPROVED} when a job is closed early.
 */
public final class StopReason {

    // ข้าม
    public static final String CUSTOMER_ABSENT = "CUSTOMER_ABSENT";
    public static final String NO_ANSWER = "NO_ANSWER";
    public static final String ADDRESS_UNCLEAR = "ADDRESS_UNCLEAR";
    public static final String ROAD_BLOCKED = "ROAD_BLOCKED";
    public static final String CUSTOMER_POSTPONED = "CUSTOMER_POSTPONED";
    // ยกเลิก
    public static final String CHANGED_MIND = "CHANGED_MIND";
    public static final String DUPLICATE_ORDER = "DUPLICATE_ORDER";
    public static final String NOT_ORDERED = "NOT_ORDERED";
    public static final String WRONG_ITEM = "WRONG_ITEM";
    // ทั้งสองอย่าง — ต้องพิมพ์รายละเอียด
    public static final String OTHER = "OTHER";
    // ระบบใส่เอง ตอนปิดงานก่อนส่งครบ
    public static final String JOB_CLOSED = "JOB_CLOSED";
    public static final String CANCEL_NOT_APPROVED = "CANCEL_NOT_APPROVED";

    public static final int NOTE_MAX = 200;

    public static final Map<String, String> SKIP_LABELS = labels(
            CUSTOMER_ABSENT, "ลูกค้าไม่อยู่",
            NO_ANSWER, "โทรไม่ติด / ไม่รับสาย",
            ADDRESS_UNCLEAR, "หาที่อยู่ไม่เจอ",
            ROAD_BLOCKED, "ทางไปไม่ได้",
            CUSTOMER_POSTPONED, "ลูกค้าขอเลื่อนวัน",
            OTHER, "อื่นๆ");

    public static final Map<String, String> CANCEL_LABELS = labels(
            CHANGED_MIND, "ลูกค้าเปลี่ยนใจ",
            DUPLICATE_ORDER, "สั่งซ้ำ",
            NOT_ORDERED, "ลูกค้าบอกว่าไม่ได้สั่ง",
            WRONG_ITEM, "ของไม่ตรงที่สั่ง",
            OTHER, "อื่นๆ");

    private static final Map<String, String> SYSTEM_LABELS = Map.of(
            JOB_CLOSED, "ปิดงานก่อนไปส่ง",
            CANCEL_NOT_APPROVED, "แจ้งยกเลิก แต่ยังไม่ได้อนุมัติตอนปิดงาน");

    private StopReason() {
    }

    /** "ลูกค้าไม่อยู่" · "อื่นๆ: ฝนตกหนัก" → the note · null when no reason. A note on another reason is appended. */
    public static String text(String reason, String note) {
        if (reason == null) {
            return null;
        }
        String trimmed = note == null || note.isBlank() ? null : note.trim();
        if (OTHER.equals(reason)) {
            return trimmed == null ? "อื่นๆ" : trimmed;
        }
        // คืนงาน (JOB_RELEASED) ใช้เหตุผลชุดเดียวกับการปฏิเสธงาน
        String label = SKIP_LABELS.getOrDefault(reason, CANCEL_LABELS.getOrDefault(reason,
                SYSTEM_LABELS.getOrDefault(reason, DeclineReason.LABELS.getOrDefault(reason, reason))));
        return trimmed == null ? label : label + " · " + trimmed;
    }

    private static Map<String, String> labels(String... pairs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return Map.copyOf(map);
    }
}
