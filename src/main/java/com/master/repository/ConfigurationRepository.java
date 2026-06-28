package com.master.repository;

import com.master.entity.Configuration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConfigurationRepository extends JpaRepository<Configuration, Long>, JpaSpecificationExecutor<Configuration> {

    Optional<Configuration> findByCode(String code);

    boolean existsByCode(String code);

    List<Configuration> findByGroupCode(String groupCode);

    List<Configuration> findByStatus(String status);

    List<Configuration> findByGroupCodeAndStatus(String groupCode, String status);

}
