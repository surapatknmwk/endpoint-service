package com.master.repository;

import com.master.entity.SubdistrictDistance;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface SubdistrictDistanceRepository extends JpaRepository<SubdistrictDistance, Long> {

    List<SubdistrictDistance> findByOriginSubdistrictSubdistrictIdAndDestinationSubdistrictSubdistrictIdIn(
            Long originSubdistrictId, Collection<Long> destinationSubdistrictIds);

    Optional<SubdistrictDistance> findByOriginSubdistrictSubdistrictIdAndDestinationSubdistrictSubdistrictId(
            Long originSubdistrictId, Long destinationSubdistrictId);

    @Query("SELECT sd FROM SubdistrictDistance sd WHERE "
            + "(:originKeyword IS NULL "
            + "OR LOWER(sd.originSubdistrict.name) LIKE LOWER(CONCAT('%', CAST(:originKeyword AS string), '%')) "
            + "OR LOWER(sd.originSubdistrict.district.name) LIKE LOWER(CONCAT('%', CAST(:originKeyword AS string), '%')) "
            + "OR LOWER(sd.originSubdistrict.district.province.name) LIKE LOWER(CONCAT('%', CAST(:originKeyword AS string), '%'))) "
            + "AND (:destinationKeyword IS NULL "
            + "OR LOWER(sd.destinationSubdistrict.name) LIKE LOWER(CONCAT('%', CAST(:destinationKeyword AS string), '%')) "
            + "OR LOWER(sd.destinationSubdistrict.district.name) LIKE LOWER(CONCAT('%', CAST(:destinationKeyword AS string), '%')) "
            + "OR LOWER(sd.destinationSubdistrict.district.province.name) LIKE LOWER(CONCAT('%', CAST(:destinationKeyword AS string), '%')))")
    Page<SubdistrictDistance> search(@Param("originKeyword") String originKeyword,
                                      @Param("destinationKeyword") String destinationKeyword,
                                      Pageable pageable);

}
