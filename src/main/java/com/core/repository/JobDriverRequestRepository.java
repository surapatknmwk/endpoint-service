package com.core.repository;

import com.core.entity.JobDriverRequest;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface JobDriverRequestRepository extends JpaRepository<JobDriverRequest, Long> {

    /**
     * Asks the driver unless the job already has a PENDING request — the partial unique index
     * {@code uq_job_driver_requests_pending} decides. Returns 1 when a row was created, 0 otherwise.
     */
    @Modifying
    @Query(value = "insert into job_driver_requests (request_id, job_id, owner_user_id, driver_user_id, "
            + "request_status, requested_at, created_by, updated_at, updated_by) "
            + "values (nextval('job_driver_requests_request_id_seq'), :jobId, :ownerUserId, :driverUserId, "
            + "'PENDING', :now, :createdBy, :now, :createdBy) "
            + "on conflict (job_id) where request_status = 'PENDING' do nothing", nativeQuery = true)
    int insertPending(@Param("jobId") Long jobId,
                      @Param("ownerUserId") Integer ownerUserId,
                      @Param("driverUserId") Integer driverUserId,
                      @Param("now") LocalDateTime now,
                      @Param("createdBy") String createdBy);

    /** The pending request of a job, locked — an owner change and a driver answer are applied one after the other. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from JobDriverRequest r where r.jobId = :jobId and r.requestStatus = 'PENDING'")
    Optional<JobDriverRequest> findPendingForUpdate(@Param("jobId") Long jobId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from JobDriverRequest r where r.requestId = :requestId")
    Optional<JobDriverRequest> findForUpdate(@Param("requestId") Long requestId);

    /** The job of a request addressed to this driver — read before locking, without loading the row. */
    @Query("select r.jobId from JobDriverRequest r where r.requestId = :requestId and r.driverUserId = :driverUserId")
    Optional<Long> findJobIdOfDriverRequest(@Param("requestId") Long requestId,
                                            @Param("driverUserId") Integer driverUserId);

    List<JobDriverRequest> findByDriverUserIdAndRequestStatusOrderByRequestedAtDesc(Integer driverUserId,
                                                                                   String requestStatus);

    Optional<JobDriverRequest> findFirstByJobIdAndRequestStatus(Long jobId, String requestStatus);

    /** The driver's newest request of a job in a status — the accepted one when the driver gives the job back. */
    Optional<JobDriverRequest> findFirstByJobIdAndDriverUserIdAndRequestStatusOrderByRequestIdDesc(
            Long jobId, Integer driverUserId, String requestStatus);

    /** Every request in a status — the expiry run reads all PENDING ones (few at a time). */
    List<JobDriverRequest> findByRequestStatus(String requestStatus);

    /** The newest request of each job (any status) — one query for a whole job list. */
    @Query(value = "select distinct on (r.job_id) r.* from job_driver_requests r where r.job_id in (:jobIds) "
            + "order by r.job_id, r.request_id desc", nativeQuery = true)
    List<JobDriverRequest> findLatestByJobIds(@Param("jobIds") Collection<Long> jobIds);

    /** Drivers who declined each of these jobs at least once. */
    @Query("select distinct r.jobId as jobId, r.driverUserId as driverUserId from JobDriverRequest r "
            + "where r.jobId in :jobIds and r.requestStatus = 'DECLINED'")
    List<JobDriver> findDeclinedDrivers(@Param("jobIds") Collection<Long> jobIds);

    /** Pending requests of one owner per driver (design G6). Drivers without one are left out. */
    @Query("select r.driverUserId as driverUserId, count(r) as pendingCount from JobDriverRequest r "
            + "where r.ownerUserId = :ownerUserId and r.requestStatus = 'PENDING' group by r.driverUserId")
    List<PendingCountByDriver> countPendingByDriver(@Param("ownerUserId") Integer ownerUserId);

    /** Whether the job has a request waiting for an answer (reminder G8). */
    boolean existsByJobIdAndRequestStatus(Long jobId, String requestStatus);

    /**
     * Cancels the pending requests between an owner and a driver — the driver left or was removed (design G5).
     * No notification: the team change has its own.
     */
    @Modifying
    @Query("update JobDriverRequest r set r.requestStatus = 'CANCELLED', r.updatedAt = :now, r.updatedBy = :by "
            + "where r.ownerUserId = :ownerUserId and r.driverUserId = :driverUserId and r.requestStatus = 'PENDING'")
    int cancelPendingOfPair(@Param("ownerUserId") Integer ownerUserId,
                            @Param("driverUserId") Integer driverUserId,
                            @Param("now") LocalDateTime now,
                            @Param("by") String by);

    /** Cancels every pending request to or from the user — the admin deleted the account (admin.md US-ADM-02). */
    @Modifying
    @Query("update JobDriverRequest r set r.requestStatus = 'CANCELLED', r.updatedAt = :now, r.updatedBy = :by "
            + "where (r.ownerUserId = :userId or r.driverUserId = :userId) and r.requestStatus = 'PENDING'")
    int cancelPendingOfUser(@Param("userId") Integer userId,
                            @Param("now") LocalDateTime now,
                            @Param("by") String by);

    interface JobDriver {
        Long getJobId();

        Integer getDriverUserId();
    }

    interface PendingCountByDriver {
        Integer getDriverUserId();

        long getPendingCount();
    }
}
