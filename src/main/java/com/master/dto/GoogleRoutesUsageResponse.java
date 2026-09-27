package com.master.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "สถิติการยิง Google Routes API จริงต่อวัน (ไม่นับผลที่อ่านจาก database) เทียบกับโควตารายวัน")
public class GoogleRoutesUsageResponse {

    @Schema(description = "โควตาสูงสุดต่อวัน (google.maps.daily-request-limit)", example = "100")
    private int dailyLimit;

    @Schema(description = "วันนี้ยิง Google ไปแล้วกี่ครั้ง (วันตามเวลา Asia/Bangkok)", example = "12")
    private int todayCallCount;

    @Schema(description = "โควตาที่เหลือของวันนี้", example = "88")
    private int todayRemaining;

    @Schema(description = "ประวัติรายวัน เรียงจากวันล่าสุด (เฉพาะวันที่มีการนับ)")
    private List<DailyUsage> history;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyUsage {

        @Schema(description = "วันที่ (Asia/Bangkok)", example = "2026-09-20")
        private LocalDate date;

        @Schema(description = "จำนวนครั้งที่ยิง Google จริงในวันนั้น", example = "12")
        private int callCount;

        @Schema(description = "จำนวนครั้งที่ต้องยิง Google แต่ถูกปฏิเสธเพราะเกินโควตา", example = "0")
        private int rejectedCount;
    }
}
