package com.core.constant;

import java.util.Map;

/** Values of {@code job_driver_requests.decline_reason} — optional when a driver declines a job (design F2). */
public final class DeclineReason {

    public static final String NOT_AVAILABLE_DATE = "NOT_AVAILABLE_DATE";
    public static final String TOO_FAR = "TOO_FAR";
    public static final String VEHICLE = "VEHICLE";
    // "อื่นๆ" — the only reason that may carry a typed note
    public static final String OTHER = "OTHER";

    /** Thai text of each reason, as shown to the owner. */
    public static final Map<String, String> LABELS = Map.of(
            NOT_AVAILABLE_DATE, "ไม่ว่างวันนั้น",
            TOO_FAR, "ไกลเกินไป",
            VEHICLE, "รถไม่พร้อม",
            OTHER, "อื่นๆ");

    private DeclineReason() {
    }

    /** "ไกลเกินไป" · "อื่นๆ: ฝนตกหนัก" (OTHER with a note shows the note) · null when no reason. */
    public static String text(String reason, String note) {
        if (reason == null) {
            return null;
        }
        if (OTHER.equals(reason) && note != null && !note.isBlank()) {
            return note.trim();
        }
        return LABELS.getOrDefault(reason, reason);
    }
}
