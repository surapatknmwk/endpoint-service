package com.search.repository;

import com.search.entity.DeliveryRouting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface SearchDeliveryRoutingRepository extends JpaRepository<DeliveryRouting, Long>, JpaSpecificationExecutor<DeliveryRouting> {
}
