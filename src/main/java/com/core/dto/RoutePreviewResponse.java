package com.core.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Route preview: every point to drive through in order (origin, stops, optional destination) and "
        + "the distance of each leg between consecutive points. Positions are subdistrict centres, not exact addresses.")
public class RoutePreviewResponse {

    @Schema(description = "Points in driving order: index 0 is the origin, then the stops, then the destination if given")
    private List<Point> points;

    @Schema(description = "Legs between consecutive points; legs[i] goes from points[i] to points[i+1]")
    private List<Leg> legs;

    @Schema(description = "Sum of the known leg distances (km)", example = "42.18")
    private BigDecimal totalKm;

    @Schema(description = "Number of legs whose distance could not be determined (not included in totalKm)", example = "0")
    private int unknownLegCount;

    public enum PointType { ORIGIN, STOP, DESTINATION }

    /** ROAD = Google road distance; STRAIGHT_LINE = estimate (Google unavailable/quota); SAME_SUBDISTRICT = 0; UNKNOWN = no data */
    public enum LegMethod { ROAD, STRAIGHT_LINE, SAME_SUBDISTRICT, UNKNOWN }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Point {
        private PointType type;
        @Schema(description = "Stop number (1, 2, 3, ...) for STOP points, null otherwise")
        private Integer stopNo;
        private Long orderId;
        private String orderCode;
        private String customerName;
        private String subdistrictCode;
        private String subdistrictName;
        private String districtName;
        private String provinceName;
        @Schema(description = "Latitude of the subdistrict centre; null when not synced yet")
        private BigDecimal lat;
        @Schema(description = "Longitude of the subdistrict centre; null when not synced yet")
        private BigDecimal lng;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Leg {
        private int fromIndex;
        private int toIndex;
        @Schema(description = "Leg distance in km; null when UNKNOWN")
        private BigDecimal distanceKm;
        private LegMethod method;
    }
}
