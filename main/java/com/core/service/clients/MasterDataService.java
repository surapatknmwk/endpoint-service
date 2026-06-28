package com.core.service.clients;

import com.core.dto.master.AddressInfoDto;
import com.core.dto.master.AddressInfoRequest;
import com.core.dto.master.DistrictDto;
import com.core.dto.master.ProvinceDto;
import com.core.dto.master.SubdistrictDto;
import com.master.entity.District;
import com.master.entity.Province;
import com.master.entity.Subdistrict;
import com.master.repository.DistrictRepository;
import com.master.repository.ProvinceRepository;
import com.master.repository.SubdistrictRepository;
import com.master.service.AddressService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * เรียกข้อมูล province/district/subdistrict แบบ in-process ผ่าน master module โดยตรง
 * (master, core, search อยู่ใน Spring Boot application เดียวกัน จึงไม่ต้องยิง HTTP ออกไปอีก service)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MasterDataService {

    private final ProvinceRepository provinceRepository;
    private final DistrictRepository districtRepository;
    private final SubdistrictRepository subdistrictRepository;
    private final AddressService addressService;

    // ==================== Province ====================
    @Transactional(readOnly = true)
    public Optional<ProvinceDto> getProvinceById(Long provinceId) {
        if (provinceId == null || provinceId == 0) {
            return Optional.empty();
        }
        return provinceRepository.findById(provinceId).map(this::toProvinceDto);
    }

    public Optional<String> getProvinceName(Long provinceId) {
        return getProvinceById(provinceId).map(ProvinceDto::getName);
    }

    @Transactional(readOnly = true)
    public List<ProvinceDto> getAllProvinces() {
        return provinceRepository.findAll().stream()
                .map(this::toProvinceDto)
                .toList();
    }

    // ==================== District ====================
    @Transactional(readOnly = true)
    public Optional<DistrictDto> getDistrictById(Long districtId) {
        if (districtId == null || districtId == 0) {
            return Optional.empty();
        }
        return districtRepository.findById(districtId).map(this::toDistrictDto);
    }

    public Optional<String> getDistrictName(Long districtId) {
        return getDistrictById(districtId).map(DistrictDto::getName);
    }

    @Transactional(readOnly = true)
    public List<DistrictDto> getDistrictsByProvinceId(Long provinceId) {
        return districtRepository.findByProvinceProvinceId(provinceId).stream()
                .map(this::toDistrictDto)
                .toList();
    }

    // ==================== Subdistrict ====================
    @Transactional(readOnly = true)
    public Optional<SubdistrictDto> getSubdistrictById(Long subdistrictId) {
        if (subdistrictId == null || subdistrictId == 0) {
            return Optional.empty();
        }
        return subdistrictRepository.findById(subdistrictId).map(this::toSubdistrictDto);
    }

    public Optional<String> getSubdistrictName(Long subdistrictId) {
        return getSubdistrictById(subdistrictId).map(SubdistrictDto::getName);
    }

    @Transactional(readOnly = true)
    public List<SubdistrictDto> getSubdistrictsByDistrictId(Long districtId) {
        return subdistrictRepository.findByDistrictDistrictId(districtId).stream()
                .map(this::toSubdistrictDto)
                .toList();
    }

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

    // ==================== Batch Get Address Info (by ID) ====================
    public List<AddressInfoDto> getAddressInfoBatch(List<AddressInfoRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return Collections.emptyList();
        }

        return requests.stream()
                .map(req -> toAddressInfoDto(addressService.getAddressInfoById(
                        req.getProvinceId(), req.getDistrictId(), req.getSubdistrictId())))
                .toList();
    }

    public List<AddressInfoDto> getAddressInfoBatchSafe(List<AddressInfoRequest> requests) {
        try {
            return getAddressInfoBatch(requests);
        } catch (Exception e) {
            log.error("Failed to get batch address info: error={}", e.getMessage());
            return Collections.emptyList();
        }
    }

    // ==================== Mappers ====================
    private ProvinceDto toProvinceDto(Province province) {
        return ProvinceDto.builder()
                .provinceId(province.getProvinceId())
                .code(province.getCode())
                .name(province.getName())
                .build();
    }

    private DistrictDto toDistrictDto(District district) {
        return DistrictDto.builder()
                .districtId(district.getDistrictId())
                .provinceId(district.getProvince() != null ? district.getProvince().getProvinceId() : null)
                .code(district.getCode())
                .name(district.getName())
                .build();
    }

    private SubdistrictDto toSubdistrictDto(Subdistrict subdistrict) {
        return SubdistrictDto.builder()
                .subdistrictId(subdistrict.getSubdistrictId())
                .districtId(subdistrict.getDistrict() != null ? subdistrict.getDistrict().getDistrictId() : null)
                .code(subdistrict.getCode())
                .name(subdistrict.getName())
                .zipCode(subdistrict.getZipCode())
                .build();
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
