package com.master.service;

import com.master.dto.AddressInfoDto;
import com.master.dto.AddressInfoRequest;
import com.master.entity.District;
import com.master.entity.Province;
import com.master.entity.Subdistrict;
import com.master.repository.DistrictRepository;
import com.master.repository.ProvinceRepository;
import com.master.repository.SubdistrictRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AddressService {

    private final ProvinceRepository provinceRepository;
    private final DistrictRepository districtRepository;
    private final SubdistrictRepository subdistrictRepository;

    /**
     * Get address info by codes (single request)
     */
    @Transactional(readOnly = true)
    public AddressInfoDto getAddressInfo(String provinceCode, String districtCode, String subdistrictCode) {
        AddressInfoDto.AddressInfoDtoBuilder builder = AddressInfoDto.builder()
                .provinceCode(provinceCode)
                .districtCode(districtCode)
                .subdistrictCode(subdistrictCode);

        if (provinceCode != null) {
            provinceRepository.findByCode(provinceCode)
                    .ifPresent(p -> builder.provinceName(p.getName()));
        }

        if (districtCode != null) {
            districtRepository.findByCode(districtCode)
                    .ifPresent(d -> builder.districtName(d.getName()));
        }

        if (subdistrictCode != null) {
            subdistrictRepository.findByCode(subdistrictCode)
                    .ifPresent(s -> {
                        builder.subdistrictName(s.getName());
                        builder.zipCode(s.getZipCode());
                    });
        }

        return builder.build();
    }

    /**
     * Get address info from request object
     */
    @Transactional(readOnly = true)
    public AddressInfoDto getAddressInfo(AddressInfoRequest request) {
        return getAddressInfo(request.getProvinceCode(), request.getDistrictCode(), request.getSubdistrictCode());
    }

    /**
     * Batch get address info - optimized to reduce database queries
     * ดึงข้อมูลทั้งหมดในครั้งเดียวแล้ว map กลับ
     */
    @Transactional(readOnly = true)
    public List<AddressInfoDto> getAddressInfoBatch(List<AddressInfoRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }

        // Collect all unique codes
        Set<String> provinceCodes = requests.stream()
                .map(AddressInfoRequest::getProvinceCode)
                .filter(code -> code != null)
                .collect(Collectors.toSet());

        Set<String> districtCodes = requests.stream()
                .map(AddressInfoRequest::getDistrictCode)
                .filter(code -> code != null)
                .collect(Collectors.toSet());

        Set<String> subdistrictCodes = requests.stream()
                .map(AddressInfoRequest::getSubdistrictCode)
                .filter(code -> code != null)
                .collect(Collectors.toSet());

        // Fetch all data in batch (3 queries instead of N*3 queries)
        Map<String, Province> provinceMap = provinceRepository.findAllByCodeIn(provinceCodes).stream()
                .collect(Collectors.toMap(Province::getCode, Function.identity()));

        Map<String, District> districtMap = districtRepository.findAllByCodeIn(districtCodes).stream()
                .collect(Collectors.toMap(District::getCode, Function.identity()));

        Map<String, Subdistrict> subdistrictMap = subdistrictRepository.findAllByCodeIn(subdistrictCodes).stream()
                .collect(Collectors.toMap(Subdistrict::getCode, Function.identity()));

        // Map results
        return requests.stream()
                .map(req -> buildAddressInfo(req, provinceMap, districtMap, subdistrictMap))
                .toList();
    }

    private AddressInfoDto buildAddressInfo(
            AddressInfoRequest request,
            Map<String, Province> provinceMap,
            Map<String, District> districtMap,
            Map<String, Subdistrict> subdistrictMap) {

        AddressInfoDto.AddressInfoDtoBuilder builder = AddressInfoDto.builder()
                .provinceCode(request.getProvinceCode())
                .districtCode(request.getDistrictCode())
                .subdistrictCode(request.getSubdistrictCode());

        if (request.getProvinceCode() != null) {
            Province province = provinceMap.get(request.getProvinceCode());
            if (province != null) {
                builder.provinceName(province.getName());
            }
        }

        if (request.getDistrictCode() != null) {
            District district = districtMap.get(request.getDistrictCode());
            if (district != null) {
                builder.districtName(district.getName());
            }
        }

        if (request.getSubdistrictCode() != null) {
            Subdistrict subdistrict = subdistrictMap.get(request.getSubdistrictCode());
            if (subdistrict != null) {
                builder.subdistrictName(subdistrict.getName());
                builder.zipCode(subdistrict.getZipCode());
            }
        }

        return builder.build();
    }

    /**
     * Get full address info by subdistrict code only
     * ดึงข้อมูลทั้งหมดจาก subdistrictCode เพียงอย่างเดียว
     */
    @Transactional(readOnly = true)
    public AddressInfoDto getFullAddressInfoBySubdistrict(String subdistrictCode) {
        if (subdistrictCode == null) {
            return AddressInfoDto.builder().build();
        }

        return subdistrictRepository.findByCode(subdistrictCode)
                .map(subdistrict -> {
                    District district = subdistrict.getDistrict();
                    Province province = district.getProvince();

                    return AddressInfoDto.builder()
                            .provinceCode(province.getCode())
                            .provinceName(province.getName())
                            .districtCode(district.getCode())
                            .districtName(district.getName())
                            .subdistrictCode(subdistrict.getCode())
                            .subdistrictName(subdistrict.getName())
                            .zipCode(subdistrict.getZipCode())
                            .build();
                })
                .orElse(AddressInfoDto.builder().subdistrictCode(subdistrictCode).build());
    }

    /**
     * Batch get full address info by subdistrict codes
     */
    @Transactional(readOnly = true)
    public List<AddressInfoDto> getFullAddressInfoBySubdistrictBatch(List<String> subdistrictCodes) {
        if (subdistrictCodes == null || subdistrictCodes.isEmpty()) {
            return List.of();
        }

        List<Subdistrict> subdistricts = subdistrictRepository.findAllByCodeIn(subdistrictCodes);

        return subdistricts.stream()
                .map(subdistrict -> {
                    District district = subdistrict.getDistrict();
                    Province province = district.getProvince();

                    return AddressInfoDto.builder()
                            .provinceCode(province.getCode())
                            .provinceName(province.getName())
                            .districtCode(district.getCode())
                            .districtName(district.getName())
                            .subdistrictCode(subdistrict.getCode())
                            .subdistrictName(subdistrict.getName())
                            .zipCode(subdistrict.getZipCode())
                            .build();
                })
                .toList();
    }

    /**
     * Get address info by IDs (not codes)
     */
    @Transactional(readOnly = true)
    public AddressInfoDto getAddressInfoById(Long provinceId, Long districtId, Long subdistrictId) {
        AddressInfoDto.AddressInfoDtoBuilder builder = AddressInfoDto.builder();

        if (provinceId != null) {
            provinceRepository.findById(provinceId)
                    .ifPresent(p -> {
                        builder.provinceCode(p.getCode());
                        builder.provinceName(p.getName());
                    });
        }

        if (districtId != null) {
            districtRepository.findById(districtId)
                    .ifPresent(d -> {
                        builder.districtCode(d.getCode());
                        builder.districtName(d.getName());
                    });
        }

        if (subdistrictId != null) {
            subdistrictRepository.findById(subdistrictId)
                    .ifPresent(s -> {
                        builder.subdistrictCode(s.getCode());
                        builder.subdistrictName(s.getName());
                        builder.zipCode(s.getZipCode());
                    });
        }

        return builder.build();
    }

    /**
     * Batch get full address info by subdistrict Code
     */
    @Transactional(readOnly = true)
    public List<AddressInfoDto> getFullAddressInfoBySubdistrictCodeBatch(Collection<String> subdistrictCodes) {
        if (subdistrictCodes == null || subdistrictCodes.isEmpty()) {
            return List.of();
        }

        List<Subdistrict> subdistricts = subdistrictRepository.findAllByCodeIn(subdistrictCodes);

        return subdistricts.stream()
                .map(subdistrict -> {
                    District district = subdistrict.getDistrict();
                    Province province = district.getProvince();

                    return AddressInfoDto.builder()
                            .provinceCode(province.getCode())
                            .provinceName(province.getName())
                            .districtCode(district.getCode())
                            .districtName(district.getName())
                            .subdistrictCode(subdistrict.getCode())
                            .subdistrictName(subdistrict.getName())
                            .zipCode(subdistrict.getZipCode())
                            .build();
                })
                .toList();
    }
}
