package com.core.repository;

import com.core.entity.DeliveryRouting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface DeliveryRoutingRepository extends JpaRepository<DeliveryRouting, Long>, JpaSpecificationExecutor<DeliveryRouting> {

    List<DeliveryRouting> findByDeliveryJobJobId(Long jobId);

    List<DeliveryRouting> findByDeliveryJobJobIdIn(List<Long> jobIds);

    List<DeliveryRouting> findByDeliveryJobJobIdOrderBySequenceNoAsc(Long jobId);

    List<DeliveryRouting> findByOrderOrderId(Long orderId);

    List<DeliveryRouting> findByOrderOrderIdIn(Collection<Long> orderIds);

    /**
     * Codes of the orders of job {@code jobId} that were put on a newer active job afterwards (a later routing row) —
     * resetting {@code jobId} would pull them back from that job (admin.md US-ADM-04).
     */
    @Query("select distinct r2.order.orderCode from DeliveryRouting r2, DeliveryRouting r1 "
            + "where r1.deliveryJob.jobId = :jobId and r2.order = r1.order "
            + "and r2.deliveryJob.jobId <> :jobId and r2.deliveryJob.status = 'A' and r2.routeId > r1.routeId")
    List<String> findOrdersMovedOnFrom(@Param("jobId") Long jobId);

    Optional<DeliveryRouting> findByDeliveryJobJobIdAndOrderOrderId(Long jobId, Long orderId);

    /**
     * จำนวนครั้งที่แต่ละ order ถูกข้าม นับจากงานที่ปิดแล้ว (ไม่นับงานที่ถูกลบ) รวมงาน {@code jobId} ที่กำลังจะปิด
     * แต่ละแถวคือ [orderId, จำนวนครั้ง] · ไม่นับจุดที่กลายเป็นข้ามเพราะปิดงาน (JOB_CLOSED / CANCEL_NOT_APPROVED)
     * — รอบนั้นไม่มีใครไปส่งจริง · ออเดอร์ที่ผู้ดูแลระบบย้อน ({@code skipsResetAt}) นับเฉพาะงานที่ปิดหลังเวลานั้น
     * (หรืองานที่กำลังปิด) — ประวัติเดิมยังอยู่ แค่ไม่นับ (admin.md)
     */
    @Query("select r.order.orderId, count(r) from DeliveryRouting r "
            + "where r.order.orderId in :orderIds and upper(r.routingStatus) = 'SKIPPED' "
            + "and r.deliveryJob.status = 'A' "
            + "and (r.statusReason is null or r.statusReason not in ('JOB_CLOSED', 'CANCEL_NOT_APPROVED')) "
            + "and (upper(r.deliveryJob.deliveryStatus) = 'COMPLETED' or r.deliveryJob.jobId = :jobId) "
            + "and (r.order.skipsResetAt is null or r.deliveryJob.jobId = :jobId "
            + "  or r.deliveryJob.completedDate > r.order.skipsResetAt) "
            + "group by r.order.orderId")
    List<Object[]> countSkipsByOrder(@Param("orderIds") List<Long> orderIds, @Param("jobId") Long jobId);
}
