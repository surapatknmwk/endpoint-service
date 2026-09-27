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
@Schema(description = "ตำแหน่ง (อำเภอ หรือ อำเภอ+ตำบล) ฝั่งต้นทาง/ปลายทาง")
public class LocationPointDto {

    @Schema(description = "รหัสจังหวัด", example = "1")
    private Long provinceId;

    @Schema(description = "รหัสอำเภอ", example = "1")
    private Long districtId;

    @Schema(description = "ชื่ออำเภอ", example = "เมืองสกลนคร")
    private String districtName;

    @Schema(description = "รหัสตำบล (ไม่ระบุ = ระดับอำเภอ)", example = "12")
    private Long subdistrictId;

    @Schema(description = "ชื่อตำบล", example = "ธาตุเชิงชุม")
    private String subdistrictName;

    @Schema(description = "ชื่อจังหวัด", example = "สกลนคร")
    private String provinceName;
}
