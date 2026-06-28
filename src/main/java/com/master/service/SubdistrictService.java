package com.master.service;

import com.master.dto.*;
import com.master.entity.District;
import com.master.entity.Subdistrict;
import com.master.exception.ResourceNotFoundException;
import com.master.repository.SubdistrictRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubdistrictService {

    private final SubdistrictRepository subdistrictRepository;

    public SubdistrictResponse getSubdistrictById(Long id) {
        Subdistrict subdistrict = subdistrictRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Subdistrict", "id", id));
        return mapToResponse(subdistrict);
    }

    public SubdistrictResponse getSubdistrictByCode(String code) {
        Subdistrict subdistrict = subdistrictRepository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Subdistrict", "code", code));
        return mapToResponse(subdistrict);
    }

    public List<SubdistrictResponse> getSubdistrictsByDistrict(Long districtId) {
        return subdistrictRepository.findByDistrictDistrictId(districtId).stream()
                .map(this::mapToResponse)
                .toList();
    }

    private SubdistrictResponse mapToResponse(Subdistrict subdistrict) {
        District district = subdistrict.getDistrict();
        return SubdistrictResponse.builder()
                .subdistrictId(subdistrict.getSubdistrictId())
                .code(subdistrict.getCode())
                .name(subdistrict.getName())
                .districtId(district.getDistrictId())
                .districtName(district.getName())
                .provinceId(district.getProvince().getProvinceId())
                .provinceName(district.getProvince().getName())
                .zipCode(subdistrict.getZipCode())
                .createdAt(subdistrict.getCreatedAt())
                .updatedAt(subdistrict.getUpdatedAt())
                .build();
    }
}
