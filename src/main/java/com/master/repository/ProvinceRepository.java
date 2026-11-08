package com.master.repository;

import com.master.entity.Province;
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
public interface ProvinceRepository extends JpaRepository<Province, Long>, JpaSpecificationExecutor<Province> {

    Optional<Province> findByCode(String code);

    List<Province> findAllByCodeIn(Collection<String> codes);

    /** Admin list (admin.md US-ADM-06): code or name contains {@code pattern} ("%text%" lower-case), by code. */
    @Query(value = "select p from Province p where lower(p.code) like :pattern or lower(p.name) like :pattern order by p.code",
            countQuery = "select count(p) from Province p where lower(p.code) like :pattern or lower(p.name) like :pattern")
    Page<Province> searchForAdmin(@Param("pattern") String pattern, Pageable pageable);

    /** Another province already has this name (trimmed, case-insensitive) — {@code excludeId} null on create. */
    @Query("select count(p) > 0 from Province p where lower(trim(p.name)) = lower(trim(:name)) "
            + "and (:excludeId is null or p.provinceId <> :excludeId)")
    boolean existsNameForAdmin(@Param("name") String name, @Param("excludeId") Long excludeId);
}
