package com.search.service.clients;

import com.master.dto.AddressInfoRequest;
import com.master.service.AddressService;
import com.search.dto.master.AddressInfoDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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

    /**
     * Resolves province/district/subdistrict names independently, so addresses
     * missing a subdistrict_code (common — it's nullable) still get province/district names.
     */
    public List<AddressInfoDto> getAddressInfoBatch(List<AddressInfoRequest> requests) {
        try {
            return addressService.getAddressInfoBatch(requests).stream()
                    .map(this::toAddressInfoDto)
                    .toList();
        } catch (Exception e) {
            log.error("Error fetching batch address info: {}", e.getMessage(), e);
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
