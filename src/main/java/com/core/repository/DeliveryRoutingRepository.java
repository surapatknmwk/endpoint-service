package com.core.repository;

import com.core.entity.DeliveryRouting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeliveryRoutingRepository extends JpaRepository<DeliveryRouting, Long>, JpaSpecificationExecutor<DeliveryRouting> {

    List<DeliveryRouting> findByDeliveryJobJobId(Long jobId);

    List<DeliveryRouting> findByDeliveryJobJobIdOrderBySequenceNoAsc(Long jobId);

    List<DeliveryRouting> findByOrderOrderId(Long orderId);

    Optional<DeliveryRouting> findByDeliveryJobJobIdAndOrderOrderId(Long jobId, Long orderId);

    List<DeliveryRouting> findByRoutingStatus(String routingStatus);

    boolean existsByDeliveryJobJobIdAndOrderOrderId(Long jobId, Long orderId);
}
