package com.master.service;

import com.common.exception.InvalidRequestBodyException;
import com.master.dto.LocationDistanceRequest;
import com.master.dto.LocationDistanceResponse;
import com.master.dto.LocationPointDto;
import com.master.dto.PageResponse;
import com.master.entity.District;
import com.master.entity.LocationDistance;
import com.master.entity.Subdistrict;
import com.master.exception.ResourceNotFoundException;
import com.master.repository.DistrictRepository;
import com.master.repository.LocationDistanceRepository;
import com.master.repository.SubdistrictRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LocationDistanceService {

    private final LocationDistanceRepository locationDistanceRepository;
    private final DistrictRepository districtRepository;
    private final SubdistrictRepository subdistrictRepository;

    @Transactional(readOnly = true)
    public PageResponse<LocationDistanceResponse> list(String keyword, Pageable pageable) {
        Page<LocationDistance> page = StringUtils.hasText(keyword)
                ? locationDistanceRepository.search(keyword.trim(), pageable)
                : locationDistanceRepository.findAll(pageable);
        return PageResponse.of(page, page.getContent().stream().map(this::mapToResponse).toList());
    }

    @Transactional(readOnly = true)
    public LocationDistanceResponse getById(Long id) {
        return mapToResponse(findEntity(id));
    }

    @Transactional(readOnly = true)
    public LocationDistanceResponse lookup(Long originDistrictId, Long originSubdistrictId,
                                            Long destinationDistrictId, Long destinationSubdistrictId) {
        if (originDistrictId == null || destinationDistrictId == null) {
            throw new InvalidRequestBodyException("ต้องระบุอำเภอต้นทางและปลายทาง");
        }
        LocationDistance entity = locationDistanceRepository
                .findBetween(originDistrictId, originSubdistrictId, destinationDistrictId, destinationSubdistrictId, null)
                .orElseThrow(() -> new ResourceNotFoundException("ไม่พบข้อมูลระยะทางระหว่างตำแหน่งที่ระบุ"));
        return mapToResponse(entity);
    }

    @Transactional
    public LocationDistanceResponse create(LocationDistanceRequest request, String username) {
        District originDistrict = validateAndGetDistrict(request.getOriginDistrictId(), "อำเภอต้นทาง");
        Subdistrict originSubdistrict = validateAndGetSubdistrict(request.getOriginSubdistrictId(), originDistrict, "ตำบลต้นทาง");
        District destinationDistrict = validateAndGetDistrict(request.getDestinationDistrictId(), "อำเภอปลายทาง");
        Subdistrict destinationSubdistrict = validateAndGetSubdistrict(request.getDestinationSubdistrictId(), destinationDistrict, "ตำบลปลายทาง");
        BigDecimal distanceKm = validateDistanceKm(request.getDistanceKm());

        validateNotSamePoint(originDistrict, originSubdistrict, destinationDistrict, destinationSubdistrict);
        validateNoDuplicate(originDistrict, originSubdistrict, destinationDistrict, destinationSubdistrict, null);

        LocationDistance entity = LocationDistance.builder()
                .originDistrict(originDistrict)
                .originSubdistrict(originSubdistrict)
                .destinationDistrict(destinationDistrict)
                .destinationSubdistrict(destinationSubdistrict)
                .distanceKm(distanceKm)
                .notes(request.getNotes())
                .status("A")
                .createdBy(username)
                .updatedBy(username)
                .build();

        entity = locationDistanceRepository.save(entity);
        log.info("Created location distance {} by {}", entity.getDistanceId(), username);
        return mapToResponse(entity);
    }

    @Transactional
    public LocationDistanceResponse update(Long id, LocationDistanceRequest request, String username) {
        LocationDistance entity = findEntity(id);

        District originDistrict = validateAndGetDistrict(request.getOriginDistrictId(), "อำเภอต้นทาง");
        Subdistrict originSubdistrict = validateAndGetSubdistrict(request.getOriginSubdistrictId(), originDistrict, "ตำบลต้นทาง");
        District destinationDistrict = validateAndGetDistrict(request.getDestinationDistrictId(), "อำเภอปลายทาง");
        Subdistrict destinationSubdistrict = validateAndGetSubdistrict(request.getDestinationSubdistrictId(), destinationDistrict, "ตำบลปลายทาง");
        BigDecimal distanceKm = validateDistanceKm(request.getDistanceKm());

        validateNotSamePoint(originDistrict, originSubdistrict, destinationDistrict, destinationSubdistrict);
        validateNoDuplicate(originDistrict, originSubdistrict, destinationDistrict, destinationSubdistrict, id);

        entity.setOriginDistrict(originDistrict);
        entity.setOriginSubdistrict(originSubdistrict);
        entity.setDestinationDistrict(destinationDistrict);
        entity.setDestinationSubdistrict(destinationSubdistrict);
        entity.setDistanceKm(distanceKm);
        entity.setNotes(request.getNotes());
        entity.setUpdatedBy(username);

        entity = locationDistanceRepository.save(entity);
        log.info("Updated location distance {} by {}", entity.getDistanceId(), username);
        return mapToResponse(entity);
    }

    @Transactional
    public void delete(Long id) {
        LocationDistance entity = findEntity(id);
        locationDistanceRepository.delete(entity);
        log.info("Deleted location distance {}", id);
    }

    // ---- helpers ----

    private LocationDistance findEntity(Long id) {
        return locationDistanceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("LocationDistance", "id", id));
    }

    private District validateAndGetDistrict(Long districtId, String label) {
        if (districtId == null) {
            throw new InvalidRequestBodyException("กรุณาระบุ" + label);
        }
        return districtRepository.findById(districtId)
                .orElseThrow(() -> new InvalidRequestBodyException("ไม่พบ" + label + "ที่ระบุ"));
    }

    private Subdistrict validateAndGetSubdistrict(Long subdistrictId, District parentDistrict, String label) {
        if (subdistrictId == null) {
            return null;
        }
        Subdistrict subdistrict = subdistrictRepository.findById(subdistrictId)
                .orElseThrow(() -> new InvalidRequestBodyException("ไม่พบ" + label + "ที่ระบุ"));
        if (!Objects.equals(subdistrict.getDistrict().getDistrictId(), parentDistrict.getDistrictId())) {
            throw new InvalidRequestBodyException(label + "ที่เลือกไม่ได้อยู่ในอำเภอที่ระบุ");
        }
        return subdistrict;
    }

    private BigDecimal validateDistanceKm(BigDecimal distanceKm) {
        if (distanceKm == null || distanceKm.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidRequestBodyException("ระยะทางต้องมากกว่า 0 กิโลเมตร");
        }
        return distanceKm;
    }

    private void validateNotSamePoint(District originDistrict, Subdistrict originSubdistrict,
                                       District destinationDistrict, Subdistrict destinationSubdistrict) {
        boolean sameDistrict = Objects.equals(originDistrict.getDistrictId(), destinationDistrict.getDistrictId());
        boolean sameSubdistrict = Objects.equals(
                originSubdistrict == null ? null : originSubdistrict.getSubdistrictId(),
                destinationSubdistrict == null ? null : destinationSubdistrict.getSubdistrictId());
        if (sameDistrict && sameSubdistrict) {
            throw new InvalidRequestBodyException("ต้นทางและปลายทางต้องไม่ใช่ตำแหน่งเดียวกัน");
        }
    }

    private void validateNoDuplicate(District originDistrict, Subdistrict originSubdistrict,
                                      District destinationDistrict, Subdistrict destinationSubdistrict,
                                      Long excludeId) {
        Optional<LocationDistance> existing = locationDistanceRepository.findBetween(
                originDistrict.getDistrictId(),
                originSubdistrict == null ? null : originSubdistrict.getSubdistrictId(),
                destinationDistrict.getDistrictId(),
                destinationSubdistrict == null ? null : destinationSubdistrict.getSubdistrictId(),
                excludeId);
        if (existing.isPresent()) {
            throw new InvalidRequestBodyException("มีข้อมูลระยะทางระหว่างตำแหน่งนี้อยู่แล้ว กรุณาแก้ไขรายการเดิมแทน");
        }
    }

    private LocationDistanceResponse mapToResponse(LocationDistance entity) {
        return LocationDistanceResponse.builder()
                .distanceId(entity.getDistanceId())
                .origin(toPointDto(entity.getOriginDistrict(), entity.getOriginSubdistrict()))
                .destination(toPointDto(entity.getDestinationDistrict(), entity.getDestinationSubdistrict()))
                .distanceKm(entity.getDistanceKm())
                .notes(entity.getNotes())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .createdBy(entity.getCreatedBy())
                .updatedBy(entity.getUpdatedBy())
                .build();
    }

    private LocationPointDto toPointDto(District district, Subdistrict subdistrict) {
        return LocationPointDto.builder()
                .provinceId(district.getProvince() == null ? null : district.getProvince().getProvinceId())
                .districtId(district.getDistrictId())
                .districtName(district.getName())
                .subdistrictId(subdistrict == null ? null : subdistrict.getSubdistrictId())
                .subdistrictName(subdistrict == null ? null : subdistrict.getName())
                .provinceName(district.getProvince() == null ? null : district.getProvince().getName())
                .build();
    }
}
