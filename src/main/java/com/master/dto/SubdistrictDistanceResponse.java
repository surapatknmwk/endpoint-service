package com.master.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Subdistrict-to-subdistrict distance response payload (read-only; filled by batch script for same-province pairs, and by Google Routes API results cached on demand)")
public class SubdistrictDistanceResponse {

    @Schema(description = "รหัสระยะทาง", example = "1")
    private Long subdistrictDistanceId;

    @Schema(description = "ต้นทาง (ตำบล)")
    private LocationPointDto origin;

    @Schema(description = "ปลายทาง (ตำบล)")
    private LocationPointDto destination;

    @Schema(description = "ระยะทาง (กิโลเมตร — เส้นตรงถ้า calcMethod = haversine, ระยะถนนถ้า google)", example = "12.34")
    private BigDecimal distanceKm;

    @Schema(description = "วิธีคำนวณ", example = "haversine")
    private String calcMethod;

    @Schema(description = "true = คู่ตำบลนี้เคยเรียกผ่าน Google Routes API แล้ว (distanceKm เป็นระยะถนนจริง ไม่ต้องเรียกซ้ำ)", example = "false")
    private boolean googleFetched;

    @Schema(description = "เวลาเดินทาง (วินาที) จาก Google — null ถ้าไม่ได้มาจาก Google", example = "6300")
    private Long durationSeconds;

    @Schema(description = "Updated timestamp")
    private LocalDateTime updatedAt;
}
