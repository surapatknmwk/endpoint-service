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
@Schema(description = "Subdistrict response payload")
public class SubdistrictResponse {

    @Schema(description = "Subdistrict ID", example = "1")
    private Long subdistrictId;

    @Schema(description = "District ID", example = "1")
    private Long districtId;

    @Schema(description = "District name", example = "เขตพระนคร")
    private String districtName;

    @Schema(description = "Province ID", example = "1")
    private Long provinceId;

    @Schema(description = "Province name", example = "กรุงเทพมหานคร")
    private String provinceName;

    @Schema(description = "Subdistrict code", example = "100101")
    private String code;

    @Schema(description = "Subdistrict name", example = "แขวงพระบรมมหาราชวัง")
    private String name;

    @Schema(description = "Zip code", example = "10200")
    private String zipCode;

    @Schema(description = "Created timestamp")
    private LocalDateTime createdAt;

    @Schema(description = "Updated timestamp")
    private LocalDateTime updatedAt;
}
