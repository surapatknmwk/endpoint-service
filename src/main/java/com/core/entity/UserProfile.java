package com.core.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Home address of a user (owner: shop / depot, the default origin of new jobs; driver: home area) and the
 * driver's availability ({@link com.core.constant.DriverAvailability}). Name and phone stay on {@code users}.
 * No row = no address and AVAILABLE; the row is created on the first save.
 */
@Entity
@Table(name = "user_profiles")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfile {

    @Id
    @Column(name = "user_id")
    private Integer userId;

    @Column(name = "address_line", length = 255)
    private String addressLine;

    @Column(name = "subdistrict_code", length = 10)
    private String subdistrictCode;

    @Column(name = "district_code", length = 10)
    private String districtCode;

    @Column(name = "province_code", length = 10)
    private String provinceCode;

    @Column(name = "availability_status", nullable = false, length = 20)
    private String availabilityStatus;

    @Column(name = "availability_updated_at")
    private LocalDateTime availabilityUpdatedAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by", length = 50)
    private String updatedBy;
}
