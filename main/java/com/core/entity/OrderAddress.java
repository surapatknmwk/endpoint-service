package com.core.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "order_addresses")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "order_addresses_seq")
    @SequenceGenerator(name = "order_addresses_seq", sequenceName = "order_addresses_address_id_seq", allocationSize = 1)
    @Column(name = "address_id")
    private Long addressId;

    @Column(name = "address_line", nullable = false, columnDefinition = "TEXT")
    private String addressLine;

    @Column(name = "subdistrict_code")
    private String subdistrictCode;

    @Column(name = "district_code")
    private String districtCode;

    @Column(name = "province_code", nullable = false)
    private String provinceCode;

    @Column(name = "zip_code", length = 10)
    private String zipCode;

    @Column(name = "map_link", length = 500)
    private String mapLink;

    @Column(name = "is_default")
    @Builder.Default
    private Boolean isDefault = false;

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
