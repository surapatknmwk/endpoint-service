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
@Schema(description = "Province request payload")
public class ProvinceRequest {

    @Schema(description = "Province code", example = "10")
    private String code;

    @Schema(description = "Province name", example = "กรุงเทพมหานคร")
    private String name;
}
