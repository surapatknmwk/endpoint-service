package com.master.repository;

import com.master.entity.DistrictDistance;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DistrictDistanceRepository extends JpaRepository<DistrictDistance, Long> {

    @Query("SELECT dd FROM DistrictDistance dd WHERE "
            + "(:originKeyword IS NULL "
            + "OR LOWER(dd.originDistrict.name) LIKE LOWER(CONCAT('%', CAST(:originKeyword AS string), '%')) "
            + "OR LOWER(dd.originDistrict.province.name) LIKE LOWER(CONCAT('%', CAST(:originKeyword AS string), '%'))) "
            + "AND (:destinationKeyword IS NULL "
            + "OR LOWER(dd.destinationDistrict.name) LIKE LOWER(CONCAT('%', CAST(:destinationKeyword AS string), '%')) "
            + "OR LOWER(dd.destinationDistrict.province.name) LIKE LOWER(CONCAT('%', CAST(:destinationKeyword AS string), '%')))")
    Page<DistrictDistance> search(@Param("originKeyword") String originKeyword,
                                   @Param("destinationKeyword") String destinationKeyword,
                                   Pageable pageable);

}
