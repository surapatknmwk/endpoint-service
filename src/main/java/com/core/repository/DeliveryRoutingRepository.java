package com.core.repository;

import com.core.entity.DeliveryRouting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeliveryRoutingRepository extends JpaRepository<DeliveryRouting, Long>, JpaSpecificationExecutor<DeliveryRouting> {

    List<DeliveryRouting> findByDeliveryJobJobId(Long jobId);

    List<DeliveryRouting> findByDeliveryJobJobIdIn(List<Long> jobIds);

    List<DeliveryRouting> findByDeliveryJobJobIdOrderBySequenceNoAsc(Long jobId);

    List<DeliveryRouting> findByOrderOrderId(Long orderId);

    Optional<DeliveryRouting> findByDeliveryJobJobIdAndOrderOrderId(Long jobId, Long orderId);

    /**
     * จำนวนครั้งที่แต่ละ order ถูกข้าม นับจากงานที่ปิดแล้ว (ไม่นับงานที่ถูกลบ) รวมงาน {@code jobId} ที่กำลังจะปิด
     * แต่ละแถวคือ [orderId, จำนวนครั้ง]
     */
    @Query("select r.order.orderId, count(r) from DeliveryRouting r "
            + "where r.order.orderId in :orderIds and upper(r.routingStatus) = 'SKIPPED' "
            + "and r.deliveryJob.status = 'A' "
            + "and (upper(r.deliveryJob.deliveryStatus) = 'COMPLETED' or r.deliveryJob.jobId = :jobId) "
            + "group by r.order.orderId")
    List<Object[]> countSkipsByOrder(@Param("orderIds") List<Long> orderIds, @Param("jobId") Long jobId);
}
