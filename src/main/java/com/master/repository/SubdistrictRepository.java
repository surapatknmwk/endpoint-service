package com.master.repository;

import com.master.entity.Subdistrict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface SubdistrictRepository extends JpaRepository<Subdistrict, Long>, JpaSpecificationExecutor<Subdistrict> {

    Optional<Subdistrict> findByCode(String code);

    List<Subdistrict> findByDistrictDistrictId(Long districtId);

    List<Subdistrict> findAllByCodeIn(Collection<String> codes);

    /** Admin list (admin.md US-ADM-06): optional district, code / name / zip contains {@code pattern}, by code. */
    @Query(value = "select s from Subdistrict s join fetch s.district d join fetch d.province "
            + "where (:districtId is null or d.districtId = :districtId) "
            + "and (lower(s.code) like :pattern or lower(s.name) like :pattern or coalesce(s.zipCode, '') like :pattern) "
            + "order by s.code",
            countQuery = "select count(s) from Subdistrict s where (:districtId is null or s.district.districtId = :districtId) "
                    + "and (lower(s.code) like :pattern or lower(s.name) like :pattern or coalesce(s.zipCode, '') like :pattern)")
    Page<Subdistrict> searchForAdmin(@Param("districtId") Long districtId, @Param("pattern") String pattern,
                                     Pageable pageable);

    /** Another subdistrict of the same district already has this name (trimmed, case-insensitive). */
    @Query("select count(s) > 0 from Subdistrict s where s.district.districtId = :districtId "
            + "and lower(trim(s.name)) = lower(trim(:name)) and (:excludeId is null or s.subdistrictId <> :excludeId)")
    boolean existsNameForAdmin(@Param("districtId") Long districtId, @Param("name") String name,
                               @Param("excludeId") Long excludeId);
}
