package com.core.repository;

import com.core.entity.DeliveryJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface DeliveryJobRepository extends JpaRepository<DeliveryJob, Long>, JpaSpecificationExecutor<DeliveryJob> {

    Optional<DeliveryJob> findByJobCode(String jobCode);

    boolean existsByJobCode(String jobCode);

    List<DeliveryJob> findByDeliveryStatus(String deliveryStatus);

    List<DeliveryJob> findByStatus(String status);

    List<DeliveryJob> findByScheduledDateBetween(LocalDateTime startDate, LocalDateTime endDate);

    List<DeliveryJob> findByDeliveryStatusAndStatus(String deliveryStatus, String status);
}
