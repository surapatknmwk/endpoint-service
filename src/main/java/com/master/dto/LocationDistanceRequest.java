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
@Schema(description = "Location distance request payload")
public class LocationDistanceRequest {

    @Schema(description = "รหัสอำเภอต้นทาง (บังคับ)", example = "1")
    private Long originDistrictId;

    @Schema(description = "รหัสตำบลต้นทาง (ไม่ระบุ = ระดับอำเภอ)", example = "12")
    private Long originSubdistrictId;

    @Schema(description = "รหัสอำเภอปลายทาง (บังคับ)", example = "2")
    private Long destinationDistrictId;

    @Schema(description = "รหัสตำบลปลายทาง (ไม่ระบุ = ระดับอำเภอ)", example = "34")
    private Long destinationSubdistrictId;

    @Schema(description = "ระยะทาง (กิโลเมตร)", example = "25.5")
    private BigDecimal distanceKm;

    @Schema(description = "หมายเหตุ")
    private String notes;
}
