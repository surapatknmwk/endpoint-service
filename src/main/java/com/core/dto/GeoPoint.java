package com.core.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Where the phone was when a stop button was pressed (browser geolocation) — optional on complete / skip / cancel
 * (driver-risk ข้อ 12, 17, 18). An out-of-range value is ignored, never refused: delivery must not depend on GPS.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Phone position when the button was pressed; null fields = unknown")
public class GeoPoint {

    @Schema(description = "Latitude", example = "17.155") private Double lat;

    @Schema(description = "Longitude", example = "104.147") private Double lng;

    @Schema(description = "Accuracy radius reported by the browser, metres", example = "25") private Double accuracyM;

    /** Usable = both coordinates present and inside the world. */
    public boolean isValid() {
        return lat != null && lng != null && lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180
                && !(lat == 0 && lng == 0);
    }
}
