package com.core.repository;

import com.core.entity.DeliveryJobEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DeliveryJobEventRepository extends JpaRepository<DeliveryJobEvent, Long> {

    List<DeliveryJobEvent> findByJobIdOrderByEventIdDesc(Long jobId);
}
