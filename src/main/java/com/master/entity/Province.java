package com.master.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "provinces")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Province {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "province_seq")
    @SequenceGenerator(name = "province_seq", sequenceName = "provinces_province_id_seq", allocationSize = 1)
    @Column(name = "province_id")
    private Long provinceId;

    @Column(name = "code", nullable = false, unique = true, length = 10)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

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
