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
@Schema(description = "District-to-district distance response payload (read-only, filled by batch script)")
public class DistrictDistanceResponse {

    @Schema(description = "รหัสระยะทาง", example = "1")
    private Long districtDistanceId;

    @Schema(description = "ต้นทาง (อำเภอ)")
    private LocationPointDto origin;

    @Schema(description = "ปลายทาง (อำเภอ)")
    private LocationPointDto destination;

    @Schema(description = "ระยะทาง (กิโลเมตร, เส้นตรง)", example = "42.15")
    private BigDecimal distanceKm;

    @Schema(description = "วิธีคำนวณ", example = "haversine")
    private String calcMethod;

    @Schema(description = "Updated timestamp")
    private LocalDateTime updatedAt;
}
