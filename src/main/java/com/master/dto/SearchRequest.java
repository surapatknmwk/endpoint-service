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
@Schema(description = "Search request with pagination")
public class SearchRequest {

    @Schema(description = "Search keyword", example = "กรุงเทพ")
    private String keyword;

    @Schema(description = "Page number (0-indexed)", example = "0")
    @Builder.Default
    private Integer page = 0;

    @Schema(description = "Page size", example = "10")
    @Builder.Default
    private Integer size = 10;

    @Schema(description = "Sort field", example = "provinceId")
    private String sortBy;

    @Schema(description = "Sort direction (ASC/DESC)", example = "ASC")
    @Builder.Default
    private String sortDirection = "ASC";
}
