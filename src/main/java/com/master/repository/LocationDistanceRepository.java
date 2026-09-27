package com.master.repository;

import com.master.entity.LocationDistance;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LocationDistanceRepository extends JpaRepository<LocationDistance, Long> {

    @Query("SELECT ld FROM LocationDistance ld WHERE "
            + "LOWER(ld.originDistrict.name) LIKE LOWER(CONCAT('%', :keyword, '%')) "
            + "OR LOWER(ld.destinationDistrict.name) LIKE LOWER(CONCAT('%', :keyword, '%')) "
            + "OR LOWER(COALESCE(ld.originSubdistrict.name, '')) LIKE LOWER(CONCAT('%', :keyword, '%')) "
            + "OR LOWER(COALESCE(ld.destinationSubdistrict.name, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    Page<LocationDistance> search(@Param("keyword") String keyword, Pageable pageable);

    // ระยะทางเป็นได้ทั้งสองทิศทาง (A->B เท่ากับ B->A) จึงต้องตรวจสอบทั้งสองด้าน
    @Query("SELECT ld FROM LocationDistance ld WHERE "
            + "(ld.distanceId <> COALESCE(:excludeId, -1)) AND ("
            + "  (ld.originDistrict.districtId = :districtA "
            + "   AND ((:subA IS NULL AND ld.originSubdistrict IS NULL) OR ld.originSubdistrict.subdistrictId = :subA) "
            + "   AND ld.destinationDistrict.districtId = :districtB "
            + "   AND ((:subB IS NULL AND ld.destinationSubdistrict IS NULL) OR ld.destinationSubdistrict.subdistrictId = :subB))"
            + "  OR "
            + "  (ld.originDistrict.districtId = :districtB "
            + "   AND ((:subB IS NULL AND ld.originSubdistrict IS NULL) OR ld.originSubdistrict.subdistrictId = :subB) "
            + "   AND ld.destinationDistrict.districtId = :districtA "
            + "   AND ((:subA IS NULL AND ld.destinationSubdistrict IS NULL) OR ld.destinationSubdistrict.subdistrictId = :subA))"
            + ")")
    Optional<LocationDistance> findBetween(@Param("districtA") Long districtA,
                                            @Param("subA") Long subA,
                                            @Param("districtB") Long districtB,
                                            @Param("subB") Long subB,
                                            @Param("excludeId") Long excludeId);

}
