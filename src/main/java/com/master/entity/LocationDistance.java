package com.master.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "location_distances")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocationDistance {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "location_distance_seq")
    @SequenceGenerator(name = "location_distance_seq", sequenceName = "location_distances_distance_id_seq", allocationSize = 1)
    @Column(name = "distance_id")
    private Long distanceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "origin_district_id", nullable = false)
    private District originDistrict;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "origin_subdistrict_id")
    private Subdistrict originSubdistrict;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_district_id", nullable = false)
    private District destinationDistrict;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_subdistrict_id")
    private Subdistrict destinationSubdistrict;

    @Column(name = "distance_km", nullable = false, precision = 10, scale = 2)
    private BigDecimal distanceKm;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "status", length = 1)
    @Builder.Default
    private String status = "A";

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by", length = 20)
    private String createdBy;

    @Column(name = "updated_by", length = 20)
    private String updatedBy;

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
