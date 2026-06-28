package com.search.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Order search request payload")
public class OrderSearchRequest {

    @Schema(hidden = true)
    private String createdBy;

    @Schema(description = "Customer Name to filter orders", example = "ส")
    private String customerName;

    @Schema(description = "Platform ID to filter orders", example = "0")
    private Long platformId;

    @Schema(description = "Province ID to filter orders", example = "47")
    private Long provinceId;

    @Schema(description = "District ID to filter orders", example = "0")
    private Long districtId;

    @Schema(description = "Subdistrict ID to filter orders", example = "0")
    private Long subdistrictId;

    @Schema(description = "Order status to filter orders", example = "PENDING")
    private String orderStatus;

    @Schema(description = "Page number (0-based)", example = "0")
    @Builder.Default
    private Integer page = 0;

    @Schema(description = "Number of items per page", example = "10")
    @Builder.Default
    private Integer size = 10;

    @Schema(description = "Sort field", example = "createdAt")
    @Builder.Default
    private String sortBy = "createdAt";

    @Schema(description = "Sort direction (asc/desc)", example = "desc")
    @Builder.Default
    private String sortDirection = "desc";
}
