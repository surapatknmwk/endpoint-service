package com.core.constant;

import java.util.List;

/**
 * Values of {@code team_members.member_status}. Only {@link #INVITED} and {@link #ACTIVE} are open; the others
 * are final and kept as history (inviting the same driver again creates a new row).
 */
public final class TeamMemberStatus {

    // เจ้าของเชิญแล้ว คนส่งยังไม่ตอบ
    public static final String INVITED = "INVITED";
    // คนส่งตอบรับแล้ว — ถูก assign งานได้
    public static final String ACTIVE = "ACTIVE";
    // คนส่งปฏิเสธคำเชิญ
    public static final String DECLINED = "DECLINED";
    // เจ้าของยกเลิกคำเชิญ
    public static final String CANCELLED = "CANCELLED";
    // เจ้าของนำออกจากทีม
    public static final String REMOVED = "REMOVED";
    // คนส่งออกจากทีมเอง
    public static final String LEFT = "LEFT";

    public static final List<String> OPEN = List.of(INVITED, ACTIVE);

    private TeamMemberStatus() {
    }
}
