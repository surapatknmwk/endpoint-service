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
@Schema(description = "Combined address information (Province, District, Subdistrict)")
public class AddressInfoDto {

    @Schema(description = "Province code", example = "10")
    private String provinceCode;

    @Schema(description = "Province name", example = "กรุงเทพมหานคร")
    private String provinceName;

    @Schema(description = "District code", example = "1001")
    private String districtCode;

    @Schema(description = "District name", example = "เขตพระนคร")
    private String districtName;

    @Schema(description = "Subdistrict code", example = "100101")
    private String subdistrictCode;

    @Schema(description = "Subdistrict name", example = "แขวงพระบรมมหาราชวัง")
    private String subdistrictName;

    @Schema(description = "Postal/Zip code", example = "10200")
    private String zipCode;
}
