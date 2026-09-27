package com.master.service;

import com.master.dto.LocationPointDto;
import com.master.dto.PageResponse;
import com.master.dto.SubdistrictDistanceResponse;
import com.master.entity.Subdistrict;
import com.master.entity.SubdistrictDistance;
import com.master.repository.SubdistrictDistanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * อ่านอย่างเดียว: ข้อมูลระยะทางระหว่างตำบล (เฉพาะคู่ที่อยู่จังหวัดเดียวกัน) มาจาก
 * db/scripts/district_distance_batch.py ซึ่งจะ upsert ทับข้อมูลทุกครั้งที่รัน
 * จึงไม่มี create/update/delete ผ่าน service นี้
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubdistrictDistanceService {

    private final SubdistrictDistanceRepository subdistrictDistanceRepository;

    @Transactional(readOnly = true)
    public PageResponse<SubdistrictDistanceResponse> list(String originKeyword, String destinationKeyword, Pageable pageable) {
        String origin = StringUtils.hasText(originKeyword) ? originKeyword.trim() : null;
        String destination = StringUtils.hasText(destinationKeyword) ? destinationKeyword.trim() : null;
        Page<SubdistrictDistance> page = (origin == null && destination == null)
                ? subdistrictDistanceRepository.findAll(pageable)
                : subdistrictDistanceRepository.search(origin, destination, pageable);
        return PageResponse.of(page, page.getContent().stream().map(this::mapToResponse).toList());
    }

    private SubdistrictDistanceResponse mapToResponse(SubdistrictDistance entity) {
        return SubdistrictDistanceResponse.builder()
                .subdistrictDistanceId(entity.getSubdistrictDistanceId())
                .origin(toPointDto(entity.getOriginSubdistrict()))
                .destination(toPointDto(entity.getDestinationSubdistrict()))
                .distanceKm(entity.getDistanceKm())
                .calcMethod(entity.getCalcMethod())
                .googleFetched(entity.isGoogleFetched())
                .durationSeconds(entity.getDurationSeconds())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private LocationPointDto toPointDto(Subdistrict subdistrict) {
        var district = subdistrict.getDistrict();
        var province = district == null ? null : district.getProvince();
        return LocationPointDto.builder()
                .provinceId(province == null ? null : province.getProvinceId())
                .provinceName(province == null ? null : province.getName())
                .districtId(district == null ? null : district.getDistrictId())
                .districtName(district == null ? null : district.getName())
                .subdistrictId(subdistrict.getSubdistrictId())
                .subdistrictName(subdistrict.getName())
                .build();
    }
}
