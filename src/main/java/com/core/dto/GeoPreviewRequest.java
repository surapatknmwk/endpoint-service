package com.core.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Address being typed in the order form — where would its pin be?")
public class GeoPreviewRequest {

    @Schema(description = "House no. / road / village as typed", example = "12 ม.3 ถ.ห้วยแก้ว")
    private String addressLine;

    @Schema(description = "Subdistrict code", example = "500101")
    private String subdistrictCode;

    @Schema(description = "Zip code", example = "50200")
    private String zipCode;

    @Schema(description = "Google Maps link, if any", example = "https://maps.app.goo.gl/abc")
    private String mapLink;
}
