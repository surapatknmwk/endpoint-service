package com.core.service.clients;

import com.core.dto.master.AddressInfoDto;
import com.master.service.AddressService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * เรียกข้อมูล province/district/subdistrict แบบ in-process ผ่าน master module โดยตรง
 * (master, core, search อยู่ใน Spring Boot application เดียวกัน จึงไม่ต้องยิง HTTP ออกไปอีก service)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MasterDataService {

    private final AddressService addressService;

    // ==================== Combined Address Info ====================
    public AddressInfoDto getAddressInfo(String provinceCode, String districtCode, String subdistrictCode) {
        return toAddressInfoDto(addressService.getAddressInfo(provinceCode, districtCode, subdistrictCode));
    }

    public AddressInfoDto getAddressInfoSafe(String provinceCode, String districtCode, String subdistrictCode) {
        try {
            return getAddressInfo(provinceCode, districtCode, subdistrictCode);
        } catch (Exception e) {
            log.error("Failed to get address info: provinceCode={}, districtCode={}, subdistrictCode={}, error={}",
                    provinceCode, districtCode, subdistrictCode, e.getMessage());
            return AddressInfoDto.builder()
                    .provinceCode(provinceCode)
                    .districtCode(districtCode)
                    .subdistrictCode(subdistrictCode)
                    .build();
        }
    }

    // ==================== Mappers ====================
    private AddressInfoDto toAddressInfoDto(com.master.dto.AddressInfoDto source) {
        return AddressInfoDto.builder()
                .provinceCode(source.getProvinceCode())
                .provinceName(source.getProvinceName())
                .districtCode(source.getDistrictCode())
                .districtName(source.getDistrictName())
                .subdistrictCode(source.getSubdistrictCode())
                .subdistrictName(source.getSubdistrictName())
                .zipCode(source.getZipCode())
                .build();
    }
}
