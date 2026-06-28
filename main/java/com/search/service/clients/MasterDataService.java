package com.search.service.clients;

import com.common.config.CacheConfig;
import com.master.service.AddressService;
import com.search.dto.master.AddressInfoDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

/**
 * เรียกข้อมูล address แบบ in-process ผ่าน master module โดยตรง
 * (master, core, search อยู่ใน Spring Boot application เดียวกัน จึงไม่ต้องยิง HTTP ออกไปอีก service)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MasterDataService {

    private final AddressService addressService;

    @Cacheable(value = CacheConfig.MASTER_DATA_CACHE, key = "#provinceCode + '-' + #districtCode + '-' + #subdistrictCode")
    public AddressInfoDto getCache(String provinceCode, String districtCode, String subdistrictCode) {

        return null;
    }

    @Cacheable(value = CacheConfig.MASTER_DATA_CACHE, key = "#provinceCode + '-' + #districtCode + '-' + #subdistrictCode")
    public AddressInfoDto getAddressInfoSafe(String provinceCode, String districtCode, String subdistrictCode) {
        try {
            return toAddressInfoDto(addressService.getAddressInfo(provinceCode, districtCode, subdistrictCode));
        } catch (Exception e) {
            log.error("Error fetching address info: provinceCode={}, districtCode={}, subdistrictCode={}",
                    provinceCode, districtCode, subdistrictCode, e);
            return null;
        }
    }

    public List<AddressInfoDto> getAddressInfoBySubdistrictCodeBatch(Collection<String> subdistrictCodes) {
        try {
            return addressService.getFullAddressInfoBySubdistrictCodeBatch(subdistrictCodes).stream()
                    .map(this::toAddressInfoDto)
                    .toList();
        } catch (Exception e) {
            log.error("Error fetching batch address info by subdistrict ids: {}", e.getMessage(), e);
            return List.of();
        }
    }

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
