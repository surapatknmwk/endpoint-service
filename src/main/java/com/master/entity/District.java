package com.master.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "districts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class District {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "district_seq")
    @SequenceGenerator(name = "district_seq", sequenceName = "districts_district_id_seq", allocationSize = 1)
    @Column(name = "district_id")
    private Long districtId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "province_id", nullable = false)
    private Province province;

    @Column(name = "code", nullable = false, unique = true, length = 10)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    // จุดศูนย์กลาง (centroid) ของอำเภอ — เติมโดย db/scripts/district_distance_batch.py (sync-districts) หรือผู้ดูแลระบบแก้เอง
    // (admin.md US-ADM-06) ใช้คำนวณ district_distances — แก้แล้วต้องรัน batch ใหม่ ระยะทางในตารางจึงจะตาม
    @Column(name = "lat", precision = 9, scale = 6)
    private BigDecimal lat;

    @Column(name = "lng", precision = 9, scale = 6)
    private BigDecimal lng;

    // 'A' = ใช้งาน · 'I' = ผู้ดูแลระบบปิดไว้ — ซ่อนจากตัวเลือกที่อยู่เท่านั้น ค้นด้วย code / id ยังเจอ (admin.md US-ADM-06)
    @Column(name = "status", nullable = false, length = 1)
    @Builder.Default
    private String status = "A";

    /** false = ปิดไว้ — ไม่แสดงในรายการให้เลือก */
    public boolean isActive() {
        return !"I".equals(status);
    }

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
