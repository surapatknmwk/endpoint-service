package com.master.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "District request payload")
public class DistrictRequest {

    @Schema(description = "Province ID", example = "1")
    private Long provinceId;

    @Schema(description = "District code", example = "1001")
    private String code;

    @Schema(description = "District name", example = "เขตพระนคร")
    private String name;
}
