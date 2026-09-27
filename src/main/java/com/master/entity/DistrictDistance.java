package com.master.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * ระยะทางเส้นตรง (Haversine) ระหว่างอำเภอทุกคู่ — เป็น master data ที่คำนวณ/เติมโดย
 * db/scripts/district_distance_batch.py เท่านั้น แอปนี้อ่านอย่างเดียว (ไม่มี create/update/delete
 * ผ่าน API เพราะข้อมูลจะถูก batch script upsert ทับอยู่แล้ว)
 */
@Entity
@Table(name = "district_distances")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DistrictDistance {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "district_distance_seq")
    @SequenceGenerator(name = "district_distance_seq", sequenceName = "district_distances_district_distance_id_seq", allocationSize = 1)
    @Column(name = "district_distance_id")
    private Long districtDistanceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "origin_district_id", nullable = false)
    private District originDistrict;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_district_id", nullable = false)
    private District destinationDistrict;

    @Column(name = "distance_km", nullable = false, precision = 10, scale = 2)
    private BigDecimal distanceKm;

    @Column(name = "calc_method", length = 20)
    @Builder.Default
    private String calcMethod = "haversine";

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
