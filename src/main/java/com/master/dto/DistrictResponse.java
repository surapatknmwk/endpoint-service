package com.master.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "District response payload")
public class DistrictResponse {

    @Schema(description = "District ID", example = "1")
    private Long districtId;

    @Schema(description = "Province ID", example = "1")
    private Long provinceId;

    @Schema(description = "Province name", example = "กรุงเทพมหานคร")
    private String provinceName;

    @Schema(description = "District code", example = "1001")
    private String code;

    @Schema(description = "District name", example = "เขตพระนคร")
    private String name;

    @Schema(description = "Created timestamp")
    private LocalDateTime createdAt;

    @Schema(description = "Updated timestamp")
    private LocalDateTime updatedAt;
}
