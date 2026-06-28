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
@Schema(description = "Request for address info lookup")
public class AddressInfoRequest {

    @Schema(description = "Province code", example = "10")
    private String provinceCode;

    @Schema(description = "District code", example = "1001")
    private String districtCode;

    @Schema(description = "Subdistrict code", example = "100101")
    private String subdistrictCode;
}
