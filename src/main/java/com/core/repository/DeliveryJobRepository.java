package com.core.repository;

import com.core.entity.DeliveryJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DeliveryJobRepository extends JpaRepository<DeliveryJob, Long>, JpaSpecificationExecutor<DeliveryJob> {

    List<DeliveryJob> findByStatusAndCreatedByOrderByCreatedAtDesc(String status, String createdBy);
}
