package com.core.repository;

import com.core.entity.DeliveryJob;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface DeliveryJobRepository extends JpaRepository<DeliveryJob, Long>, JpaSpecificationExecutor<DeliveryJob> {

    List<DeliveryJob> findByStatusAndCreatedByOrderByCreatedAtDesc(String status, String createdBy);

    /**
     * The job row, locked — choosing a driver and the driver accepting are applied one after the other
     * (both lock the job first, then its pending request).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from DeliveryJob j where j.jobId = :jobId")
    Optional<DeliveryJob> findForUpdate(@Param("jobId") Long jobId);

    /** Active jobs in the given delivery status whose scheduled date is in [from, to). */
    @Query("select j from DeliveryJob j where j.status = 'A' and j.deliveryStatus = :deliveryStatus "
            + "and j.scheduledDate >= :from and j.scheduledDate < :to order by j.jobId")
    List<DeliveryJob> findScheduledBetween(@Param("deliveryStatus") String deliveryStatus,
                                           @Param("from") LocalDateTime from,
                                           @Param("to") LocalDateTime to);

    /**
     * Active jobs (any delivery status) assigned to the driver by owners whose team they are still ACTIVE in,
     * newest first. Leaving or being removed hides that owner's jobs, closed ones included.
     */
    @Query(value = "select j.* from delivery_jobs j where j.status = 'A' and j.assigned_driver_id = :driverUserId "
            + "and exists (select 1 from team_members t join users u on u.user_id = t.owner_user_id "
            + "  where u.username = j.created_by and t.member_user_id = :driverUserId and t.member_status = 'ACTIVE') "
            + "order by j.created_at desc", nativeQuery = true)
    List<DeliveryJob> findAssignedToDriver(@Param("driverUserId") Integer driverUserId);

    /**
     * Of these drivers, the ones delivering now ("กำลังส่งงาน"): an active IN_PROGRESS job assigned to them by an
     * owner whose team they are still ACTIVE in — any owner.
     */
    @Query(value = "select distinct j.assigned_driver_id from delivery_jobs j "
            + "join users o on o.username = j.created_by "
            + "join team_members t on t.owner_user_id = o.user_id and t.member_user_id = j.assigned_driver_id "
            + "  and t.member_status = 'ACTIVE' "
            + "where j.status = 'A' and j.delivery_status = 'IN_PROGRESS' and j.assigned_driver_id in (:driverUserIds)",
            nativeQuery = true)
    List<Integer> findDeliveringDrivers(@Param("driverUserIds") Collection<Integer> driverUserIds);

    /** Active, not completed jobs of one owner assigned to one driver. */
    @Query("select count(j) from DeliveryJob j where j.status = 'A' and j.deliveryStatus <> 'COMPLETED' "
            + "and j.createdBy = :ownerUsername and j.assignedDriverId = :driverUserId")
    long countOpenAssigned(@Param("ownerUsername") String ownerUsername, @Param("driverUserId") Integer driverUserId);

    /**
     * The jobs {@link #countOpenAssigned} counts, for every driver of one owner, each with its stop progress
     * (all stops / delivered stops — same numbers as the job card). Unordered; drivers without open jobs are absent.
     */
    @Query("select j.assignedDriverId as driverUserId, j.jobId as jobId, j.jobCode as jobCode, j.jobName as jobName, "
            + "j.deliveryStatus as deliveryStatus, j.scheduledDate as scheduledDate, count(r) as stopCount, "
            + "coalesce(sum(case when r.routingStatus = 'COMPLETED' then 1 else 0 end), 0) as completedStopCount "
            + "from DeliveryJob j left join j.routings r "
            + "where j.status = 'A' and j.deliveryStatus <> 'COMPLETED' "
            + "and j.createdBy = :ownerUsername and j.assignedDriverId is not null "
            + "group by j.assignedDriverId, j.jobId, j.jobCode, j.jobName, j.deliveryStatus, j.scheduledDate")
    List<OpenAssignedJob> findOpenAssignedByOwner(@Param("ownerUsername") String ownerUsername);

    /** {@link #countOpenAssigned} for every owner of one driver. Owners without open jobs are left out. */
    @Query("select j.createdBy as ownerUsername, count(j) as openJobCount from DeliveryJob j "
            + "where j.status = 'A' and j.deliveryStatus <> 'COMPLETED' "
            + "and j.assignedDriverId = :driverUserId group by j.createdBy")
    List<OpenJobCountByOwner> countOpenAssignedByOwner(@Param("driverUserId") Integer driverUserId);

    interface OpenAssignedJob {
        Integer getDriverUserId();

        Long getJobId();

        String getJobCode();

        String getJobName();

        String getDeliveryStatus();

        LocalDateTime getScheduledDate();

        long getStopCount();

        long getCompletedStopCount();
    }

    interface OpenJobCountByOwner {
        String getOwnerUsername();

        long getOpenJobCount();
    }
}
