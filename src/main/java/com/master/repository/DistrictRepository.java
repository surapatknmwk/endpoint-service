package com.master.repository;

import com.master.entity.District;
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
public interface DistrictRepository extends JpaRepository<District, Long>, JpaSpecificationExecutor<District> {

    Optional<District> findByCode(String code);

    List<District> findByProvinceProvinceId(Long provinceId);

    List<District> findAllByCodeIn(Collection<String> codes);

    /** Admin list (admin.md US-ADM-06): optional province, code or name contains {@code pattern}, by code. */
    @Query(value = "select d from District d join fetch d.province p where (:provinceId is null or p.provinceId = :provinceId) "
            + "and (lower(d.code) like :pattern or lower(d.name) like :pattern) order by d.code",
            countQuery = "select count(d) from District d where (:provinceId is null or d.province.provinceId = :provinceId) "
                    + "and (lower(d.code) like :pattern or lower(d.name) like :pattern)")
    Page<District> searchForAdmin(@Param("provinceId") Long provinceId, @Param("pattern") String pattern, Pageable pageable);

    /** Another district of the same province already has this name (trimmed, case-insensitive). */
    @Query("select count(d) > 0 from District d where d.province.provinceId = :provinceId "
            + "and lower(trim(d.name)) = lower(trim(:name)) and (:excludeId is null or d.districtId <> :excludeId)")
    boolean existsNameForAdmin(@Param("provinceId") Long provinceId, @Param("name") String name,
                               @Param("excludeId") Long excludeId);
}
