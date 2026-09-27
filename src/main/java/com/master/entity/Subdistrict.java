package com.master.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "subdistricts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Subdistrict {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "subdistrict_seq")
    @SequenceGenerator(name = "subdistrict_seq", sequenceName = "subdistricts_subdistrict_id_seq", allocationSize = 1)
    @Column(name = "subdistrict_id")
    private Long subdistrictId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "district_id", nullable = false)
    private District district;

    @Column(name = "code", nullable = false, unique = true, length = 10)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "zip_code", length = 10)
    private String zipCode;

    // จุดศูนย์กลาง (centroid) ของตำบล เติมโดย db/scripts/district_distance_batch.py
    // (sync-subdistricts) ใช้คำนวณระยะทาง Haversine — อาจเป็น NULL ถ้ายังไม่เคย sync
    @Column(name = "lat", precision = 9, scale = 6)
    private BigDecimal lat;

    @Column(name = "lng", precision = 9, scale = 6)
    private BigDecimal lng;

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
