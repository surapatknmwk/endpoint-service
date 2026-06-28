package com.master.repository;

import com.master.entity.Subdistrict;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface SubdistrictRepository extends JpaRepository<Subdistrict, Long>, JpaSpecificationExecutor<Subdistrict> {

    Optional<Subdistrict> findByCode(String code);

    boolean existsByCode(String code);

    List<Subdistrict> findByDistrictDistrictId(Long districtId);

    List<Subdistrict> findByZipCode(String zipCode);

    List<Subdistrict> findAllByCodeIn(Collection<String> codes);

}
