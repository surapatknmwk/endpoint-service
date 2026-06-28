package com.core.dto.master;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DistrictDto {
    private Long districtId;
    private Long provinceId;
    private String code;
    private String name;
}
