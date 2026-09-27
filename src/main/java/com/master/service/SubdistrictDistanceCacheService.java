package com.master.service;

import com.master.entity.Subdistrict;
import com.master.entity.SubdistrictDistance;
import com.master.repository.SubdistrictDistanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * บันทึกผลลัพธ์จาก Google Routes API (ดู {@link GoogleRoutesService}) ลง
 * {@code subdistrict_distances} พร้อม flag {@code googleFetched = true} ใน transaction ของตัวเอง
 * ({@code REQUIRES_NEW}) แยกจาก transaction ของผู้เรียก (เช่น {@code JobService.getCandidateOrders})
 * — ถ้า insert ชนกัน (race condition ตอนสองคำขอ cache คู่เดียวกันพร้อมกัน) ความล้มเหลวจะ mark แค่
 * transaction ใหม่นี้เป็น rollback-only ไม่ใช่ transaction หลักของผู้เรียก เหมือนเหตุผลเดียวกับที่
 * {@link GoogleRoutesService#findRoute} ใช้ {@code REQUIRES_NEW}
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubdistrictDistanceCacheService {

    static final String CALC_METHOD_GOOGLE = "google";

    private final SubdistrictDistanceRepository subdistrictDistanceRepository;

    /** แถวของคู่นี้ที่เคยเรียก Google แล้ว ({@code googleFetched = true}) — ว่างถ้ายังไม่เคย */
    @Transactional(readOnly = true)
    public Optional<SubdistrictDistance> findGoogleFetched(Long originSubdistrictId, Long destinationSubdistrictId) {
        return subdistrictDistanceRepository
                .findByOriginSubdistrictSubdistrictIdAndDestinationSubdistrictSubdistrictId(
                        originSubdistrictId, destinationSubdistrictId)
                .filter(SubdistrictDistance::isGoogleFetched);
    }

    /**
     * บันทึกระยะทางจาก Google ของคู่ origin→destination (ทิศทางเดียว — ผู้เรียกเรียกซ้ำสำหรับทิศตรงข้าม)
     * <ul>
     *   <li>ยังไม่มีแถว → สร้างใหม่</li>
     *   <li>มีแถว haversine เดิม → ทับด้วยค่าจาก Google แล้วตั้ง flag</li>
     *   <li>มีแถวที่เคยเรียก Google แล้ว → ไม่ทำอะไร (ไม่ทับค่าเดิม)</li>
     * </ul>
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveGoogleResult(Subdistrict origin, Subdistrict destination, BigDecimal distanceKm, Long durationSeconds) {
        Optional<SubdistrictDistance> existing = subdistrictDistanceRepository
                .findByOriginSubdistrictSubdistrictIdAndDestinationSubdistrictSubdistrictId(
                        origin.getSubdistrictId(), destination.getSubdistrictId());
        if (existing.isPresent()) {
            SubdistrictDistance row = existing.get();
            if (row.isGoogleFetched()) {
                return;
            }
            row.setDistanceKm(distanceKm);
            row.setDurationSeconds(durationSeconds);
            row.setCalcMethod(CALC_METHOD_GOOGLE);
            row.setGoogleFetched(true);
            return;
        }
        try {
            subdistrictDistanceRepository.saveAndFlush(SubdistrictDistance.builder()
                    .originSubdistrict(origin)
                    .destinationSubdistrict(destination)
                    .distanceKm(distanceKm)
                    .durationSeconds(durationSeconds)
                    .calcMethod(CALC_METHOD_GOOGLE)
                    .googleFetched(true)
                    .build());
        } catch (DataIntegrityViolationException ex) {
            log.debug("subdistrict_distances pair {} -> {} already cached concurrently",
                    origin.getCode(), destination.getCode());
        }
    }
}
