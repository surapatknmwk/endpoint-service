package com.core.dto.master;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubdistrictDto {
    private Long subdistrictId;
    private Long districtId;
    private String code;
    private String name;
    private String zipCode;
}
