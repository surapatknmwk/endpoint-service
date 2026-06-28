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
@Schema(description = "Subdistrict request payload")
public class SubdistrictRequest {

    @Schema(description = "District ID", example = "1")
    private Long districtId;

    @Schema(description = "Subdistrict code", example = "100101")
    private String code;

    @Schema(description = "Subdistrict name", example = "แขวงพระบรมมหาราชวัง")
    private String name;

    @Schema(description = "Zip code", example = "10200")
    private String zipCode;
}
