package com.master.service;

import com.master.dto.*;
import com.master.entity.District;
import com.master.exception.ResourceNotFoundException;
import com.master.repository.DistrictRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DistrictService {

    private final DistrictRepository districtRepository;

    public DistrictResponse getDistrictById(Long id) {
        District district = districtRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("District", "id", id));
        return mapToResponse(district);
    }

    public DistrictResponse getDistrictByCode(String code) {
        District district = districtRepository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("District", "code", code));
        return mapToResponse(district);
    }

    public List<DistrictResponse> getDistrictsByProvince(Long provinceId) {
        return districtRepository.findByProvinceProvinceId(provinceId).stream()
                .map(this::mapToResponse)
                .toList();
    }

    private DistrictResponse mapToResponse(District district) {
        return DistrictResponse.builder()
                .districtId(district.getDistrictId())
                .code(district.getCode())
                .name(district.getName())
                .provinceId(district.getProvince().getProvinceId())
                .provinceName(district.getProvince().getName())
                .createdAt(district.getCreatedAt())
                .updatedAt(district.getUpdatedAt())
                .build();
    }
}
