package com.core.dto;

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
@Schema(description = "Pin the order would get when saved (same rules as saving, nothing is stored)")
public class GeoPreviewResponse {

    @Schema(description = "Latitude; null when nothing could be located", example = "18.795300")
    private BigDecimal lat;

    @Schema(description = "Longitude; null when nothing could be located", example = "98.962000")
    private BigDecimal lng;

    @Schema(description = "MAP_LINK (pin in the link) / GEOCODE (found from the address text) / SUBDISTRICT "
            + "(not found — subdistrict centre shown instead)", example = "GEOCODE")
    private String source;

    @Schema(description = "Google's precision for GEOCODE: ROOFTOP / RANGE_INTERPOLATED / GEOMETRIC_CENTER / APPROXIMATE",
            example = "ROOFTOP")
    private String locationType;

    @Schema(description = "Why the address could not be used (source = SUBDISTRICT): NO_ADDRESS / NOT_FOUND / "
            + "APPROXIMATE / AREA_ONLY / TOO_FAR / ERROR", example = "APPROXIMATE")
    private String note;
}
