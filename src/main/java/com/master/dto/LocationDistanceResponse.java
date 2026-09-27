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
@Schema(description = "Location distance response payload")
public class LocationDistanceResponse {

    @Schema(description = "รหัสระยะทาง", example = "1")
    private Long distanceId;

    @Schema(description = "ต้นทาง")
    private LocationPointDto origin;

    @Schema(description = "ปลายทาง")
    private LocationPointDto destination;

    @Schema(description = "ระยะทาง (กิโลเมตร)", example = "25.5")
    private BigDecimal distanceKm;

    @Schema(description = "หมายเหตุ")
    private String notes;

    @Schema(description = "Created timestamp")
    private LocalDateTime createdAt;

    @Schema(description = "Updated timestamp")
    private LocalDateTime updatedAt;

    @Schema(description = "สร้างโดย")
    private String createdBy;

    @Schema(description = "แก้ไขล่าสุดโดย")
    private String updatedBy;
}
