package com.master.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * ระยะทางระหว่างตำบลแต่ละคู่ ส่วนใหญ่เป็นระยะเส้นตรง (Haversine, {@code calcMethod ==
 * "haversine"}) ที่คำนวณ/เติมล่วงหน้าแบบ offline โดย db/scripts/district_distance_batch.py
 * (sync-subdistricts / compute-subdistrict-distances) — ครอบคลุมเฉพาะคู่ตำบล "ในจังหวัด
 * เดียวกัน" เท่านั้น
 *
 * ทุกครั้งที่แอปเรียก Google Routes API ผ่าน {@link com.master.service.GoogleRoutesService#findRoute}
 * (ทั้ง endpoint /google-distance และ {@link com.core.service.JobService#getCandidateOrders})
 * ผลลัพธ์จะถูกบันทึกกลับที่นี่ทั้งสองทิศทาง — สร้างแถวใหม่ หรือทับแถว haversine เดิมของคู่นั้น —
 * แล้วตั้ง {@code calcMethod == "google"} และ {@code googleFetched == true} เพื่อไม่ต้องยิง API
 * ซ้ำในครั้งถัดไป ตารางนี้จึงไม่ใช่ read-only จากฝั่งแอปอีกต่อไป
 */
@Entity
@Table(name = "subdistrict_distances")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubdistrictDistance {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "subdistrict_distance_seq")
    @SequenceGenerator(name = "subdistrict_distance_seq", sequenceName = "subdistrict_distances_subdistrict_distance_id_seq", allocationSize = 1)
    @Column(name = "subdistrict_distance_id")
    private Long subdistrictDistanceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "origin_subdistrict_id", nullable = false)
    private Subdistrict originSubdistrict;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_subdistrict_id", nullable = false)
    private Subdistrict destinationSubdistrict;

    @Column(name = "distance_km", nullable = false, precision = 10, scale = 2)
    private BigDecimal distanceKm;

    @Column(name = "calc_method", length = 20)
    @Builder.Default
    private String calcMethod = "haversine";

    /**
     * {@code true} = คู่ตำบลนี้เคยถูกเรียกผ่าน Google Routes API แล้ว ({@code distanceKm} /
     * {@code durationSeconds} เป็นระยะถนนจริงจาก Google) ไม่ต้องเรียก Google ซ้ำ
     */
    @Column(name = "google_fetched", nullable = false)
    @Builder.Default
    private boolean googleFetched = false;

    /** เวลาเดินทาง (วินาที) จาก Google — {@code null} ถ้าไม่ใช่แถวจาก Google หรือ cache ไว้ก่อนมี column นี้ */
    @Column(name = "duration_seconds")
    private Long durationSeconds;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
