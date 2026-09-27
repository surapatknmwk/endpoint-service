package com.core.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Route preview request: origin, the selected orders in delivery order, and an optional final destination")
public class RoutePreviewRequest {

    @Schema(description = "Origin (starting point) subdistrict code", example = "440404")
    private String originSubdistrictCode;

    @Schema(description = "Optional final destination subdistrict code (destination-specified mode)", example = "440601")
    private String destinationSubdistrictCode;

    @Schema(description = "Selected order IDs, in delivery stop order")
    private List<Long> orderIds;
}
