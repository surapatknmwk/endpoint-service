package com.master.repository;

import com.master.entity.District;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface DistrictRepository extends JpaRepository<District, Long>, JpaSpecificationExecutor<District> {

    Optional<District> findByCode(String code);

    boolean existsByCode(String code);

    List<District> findByProvinceProvinceId(Long provinceId);

    List<District> findAllByCodeIn(Collection<String> codes);

}
