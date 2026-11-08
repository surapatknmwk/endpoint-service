package com.core.dto.admin;

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
@Schema(description = "A province / district / subdistrict in the admin address list (admin.md US-ADM-06)")
public class AdminAreaResponse {

    @Schema(description = "provinceId / districtId / subdistrictId", example = "120")
    private Long id;

    @Schema(example = "470101")
    private String code;

    @Schema(example = "ธาตุเชิงชุม")
    private String name;

    @Schema(description = "Subdistrict only", example = "47000")
    private String zipCode;

    @Schema(description = "District / subdistrict centroid; null = distances cannot be estimated")
    private BigDecimal lat;

    private BigDecimal lng;

    @Schema(description = "false = disabled: hidden from the address pickers")
    private Boolean active;

    @Schema(description = "District: its province · subdistrict: its district")
    private Long parentId;

    private String parentName;

    @Schema(description = "false = the parent is disabled (this row is unreachable in the pickers too)")
    private Boolean parentActive;

    @Schema(description = "Subdistrict only: name of its province")
    private String provinceName;
}
