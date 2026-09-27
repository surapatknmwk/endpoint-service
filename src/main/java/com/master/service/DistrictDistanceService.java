package com.master.service;

import com.master.dto.DistrictDistanceResponse;
import com.master.dto.LocationPointDto;
import com.master.dto.PageResponse;
import com.master.entity.District;
import com.master.entity.DistrictDistance;
import com.master.repository.DistrictDistanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * อ่านอย่างเดียว: ข้อมูลระยะทางระหว่างอำเภอมาจาก db/scripts/district_distance_batch.py
 * ซึ่งจะ upsert ทับข้อมูลทุกครั้งที่รัน จึงไม่มี create/update/delete ผ่าน service นี้
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DistrictDistanceService {

    private final DistrictDistanceRepository districtDistanceRepository;

    @Transactional(readOnly = true)
    public PageResponse<DistrictDistanceResponse> list(String originKeyword, String destinationKeyword, Pageable pageable) {
        String origin = StringUtils.hasText(originKeyword) ? originKeyword.trim() : null;
        String destination = StringUtils.hasText(destinationKeyword) ? destinationKeyword.trim() : null;
        Page<DistrictDistance> page = (origin == null && destination == null)
                ? districtDistanceRepository.findAll(pageable)
                : districtDistanceRepository.search(origin, destination, pageable);
        return PageResponse.of(page, page.getContent().stream().map(this::mapToResponse).toList());
    }

    private DistrictDistanceResponse mapToResponse(DistrictDistance entity) {
        return DistrictDistanceResponse.builder()
                .districtDistanceId(entity.getDistrictDistanceId())
                .origin(toPointDto(entity.getOriginDistrict()))
                .destination(toPointDto(entity.getDestinationDistrict()))
                .distanceKm(entity.getDistanceKm())
                .calcMethod(entity.getCalcMethod())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private LocationPointDto toPointDto(District district) {
        return LocationPointDto.builder()
                .provinceId(district.getProvince() == null ? null : district.getProvince().getProvinceId())
                .districtId(district.getDistrictId())
                .districtName(district.getName())
                .provinceName(district.getProvince() == null ? null : district.getProvince().getName())
                .build();
    }
}
