package com.core.repository;

import com.core.entity.OrderAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import org.springframework.stereotype.Repository;


@Repository
public interface OrderAddressRepository extends JpaRepository<OrderAddress, Long>, JpaSpecificationExecutor<OrderAddress> {

    /**
     * ที่อยู่เดียวกัน (ข้อความที่อยู่ไม่สนตัวพิมพ์/ช่องว่างหัวท้าย + ตำบลเดียวกัน) ของเจ้าของคนนี้ ที่เคยหาพิกัดจาก Google
     * ได้แล้ว — ล่าสุดก่อน · ใช้พิกัดเดิมแทนการเรียก Geocoding ซ้ำ (OrderGeoLocator)
     */
    @Query(value = "SELECT * FROM order_addresses a"
            + " WHERE a.created_by = :createdBy AND a.subdistrict_code = :subdistrictCode"
            + "   AND a.geo_source = 'GEOCODE' AND a.lat IS NOT NULL AND a.lng IS NOT NULL"
            + "   AND lower(btrim(a.address_line)) = lower(btrim(:addressLine))"
            + " ORDER BY a.updated_at DESC NULLS LAST LIMIT 1", nativeQuery = true)
    Optional<OrderAddress> findGeocodedSameAddress(@Param("createdBy") String createdBy,
                                                   @Param("subdistrictCode") String subdistrictCode,
                                                   @Param("addressLine") String addressLine);
}
