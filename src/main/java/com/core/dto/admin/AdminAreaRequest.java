package com.core.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Add / edit a province, district or subdistrict (admin.md US-ADM-06). "
        + "code and parentId are read on create only — they cannot be changed afterwards.")
public class AdminAreaRequest {

    @Schema(description = "Create a district: provinceId · create a subdistrict: districtId", example = "120")
    private Long parentId;

    @Schema(description = "Create only. Province 2 digits, district 4 (starts with the province code), "
            + "subdistrict 6 (starts with the district code)", example = "470199")
    private String code;

    @NotBlank(message = "กรุณากรอกชื่อ")
    @Size(max = 100, message = "ชื่อยาวเกิน 100 ตัวอักษร")
    private String name;

    @Schema(description = "Subdistrict only: 5 digits, empty = none", example = "47000")
    private String zipCode;

    @Schema(description = "District / subdistrict: both or neither, lat -90..90, lng -180..180", example = "17.1545")
    private BigDecimal lat;

    @Schema(example = "104.1348")
    private BigDecimal lng;
}
