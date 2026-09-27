package com.master.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * จำนวนครั้งที่แอปยิง Google Routes API จริงในแต่ละวัน (วันตามเวลา Asia/Bangkok) — เป็นทั้งตัวนับโควตา
 * รายวันและ log ไว้ดูย้อนหลัง อ่านอย่างเดียวจากฝั่ง JPA; การเพิ่มตัวนับทำผ่าน atomic upsert ใน
 * {@link com.master.repository.GoogleRoutesUsageRepository} เพื่อไม่ให้ request พร้อมกันนับพลาด
 */
@Entity
@Immutable
@Table(name = "google_routes_usage")
@Getter
@NoArgsConstructor
public class GoogleRoutesUsage {

    @Id
    @Column(name = "usage_date")
    private LocalDate usageDate;

    @Column(name = "call_count", nullable = false)
    private int callCount;

    @Column(name = "rejected_count", nullable = false)
    private int rejectedCount;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
