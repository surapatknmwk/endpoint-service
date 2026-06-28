package com.search.repository;

import com.search.entity.DeliveryJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface SearchDeliveryJobRepository extends JpaRepository<DeliveryJob, Long>, JpaSpecificationExecutor<DeliveryJob> {
}
