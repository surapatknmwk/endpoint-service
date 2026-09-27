package com.master.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "ระยะทาง/เวลาเดินทางจริงตามถนน ดึงจาก Google Routes API (computeRoutes) โดยตรง — ต่างจาก "
        + "/calculate (เส้นตรง Haversine) และ /route (เดินผ่านตำบลติดกันจริงที่เราคำนวณเอง) ตรงที่นี่เป็นค่าจริงจาก "
        + "routing engine ของ Google เอง ไม่ใช่ค่าประมาณ ผลที่เคยเรียกแล้วจะถูกเก็บลง database และใช้ซ้ำโดยไม่เรียก Google อีก")
public class GoogleRouteDistanceResponse {

    @Schema(description = "ต้นทาง (ตำบล)")
    private LocationPointDto origin;

    @Schema(description = "ปลายทาง (ตำบล)")
    private LocationPointDto destination;

    @Schema(description = "ระยะทางจริงตามถนน (เมตร) ตามที่ Google ตอบกลับมา", example = "112340")
    private Integer distanceMeters;

    @Schema(description = "ระยะทางจริงตามถนน (กม.)", example = "112.34")
    private BigDecimal distanceKm;

    @Schema(description = "เวลาเดินทางโดยประมาณ (วินาที) -- ไม่รวมสภาพจราจร (routingPreference: TRAFFIC_UNAWARE, "
            + "อยู่ใน Essentials tier ราคาถูกกว่า TRAFFIC_AWARE ซึ่งเป็น Pro tier)", example = "6300")
    private Long durationSeconds;

    @Schema(description = "true = อ่านจากผลที่เคยเรียก Google แล้วและเก็บไว้ใน database (ไม่ได้เรียก Google ครั้งนี้), "
            + "false = เรียก Google สดครั้งนี้ (และบันทึกผลไว้สำหรับครั้งถัดไป)", example = "false")
    private Boolean fromCache;
}
