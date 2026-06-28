package com.core.dto.master;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddressInfoRequest {
    private Long provinceId;
    private Long districtId;
    private Long subdistrictId;
}
