package com.master.repository;

import com.master.entity.Province;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProvinceRepository extends JpaRepository<Province, Long>, JpaSpecificationExecutor<Province> {

    Optional<Province> findByCode(String code);

    boolean existsByCode(String code);

    List<Province> findAllByCodeIn(Collection<String> codes);

}
