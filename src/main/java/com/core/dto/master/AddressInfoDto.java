package com.core.dto.master;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddressInfoDto {
    private String provinceCode;
    private String provinceName;
    private String districtCode;
    private String districtName;
    private String subdistrictCode;
    private String subdistrictName;
    private String zipCode;
}
