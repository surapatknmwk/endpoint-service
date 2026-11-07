package com.core.service.clients;

import com.core.dto.master.AddressInfoDto;
import com.master.service.AddressService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

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

    // ==================== By subdistrict ====================

    /** Full address (names and parent codes) of a subdistrict; empty when the code does not exist. */
    public Optional<AddressInfoDto> findBySubdistrict(String subdistrictCode) {
        if (subdistrictCode == null) {
            return Optional.empty();
        }
        return Optional.of(toAddressInfoDto(addressService.getFullAddressInfoBySubdistrict(subdistrictCode)))
                .filter(a -> a.getProvinceCode() != null);
    }

    /** {@link #findBySubdistrict} for many codes in one query; unknown codes are left out. */
    public Map<String, AddressInfoDto> findBySubdistricts(Collection<String> subdistrictCodes) {
        return addressService.getFullAddressInfoBySubdistrictCodeBatch(subdistrictCodes).stream()
                .map(this::toAddressInfoDto)
                .collect(Collectors.toMap(AddressInfoDto::getSubdistrictCode, Function.identity(), (a, b) -> a));
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
