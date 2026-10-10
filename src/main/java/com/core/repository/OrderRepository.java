package com.core.repository;

import com.core.entity.Order;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {

    /**
     * The given user's own orders that can still be put on a job, PENDING (skipped on an earlier job) first and
     * then oldest first, so the {@code pageable} cut keeps the orders that have waited longest.
     * Address and customer are fetched in the same query to avoid N+1 when ranking.
     */
    @Query("SELECT o FROM Order o JOIN FETCH o.address a LEFT JOIN FETCH o.customer "
            + "WHERE o.status = 'A' "
            + "AND o.createdBy = :createdBy "
            + "AND UPPER(o.orderStatus) IN ('NEW', 'PENDING') "
            + "AND a.provinceCode IN :provinceCodes "
            + "AND (:districtCode IS NULL OR a.districtCode = :districtCode) "
            + "AND (:subdistrictCode IS NULL OR a.subdistrictCode = :subdistrictCode) "
            + "ORDER BY CASE WHEN UPPER(o.orderStatus) = 'PENDING' THEN 0 ELSE 1 END, o.createdAt ASC, o.orderId ASC")
    List<Order> findCandidateOrdersForJob(@Param("provinceCodes") Collection<String> provinceCodes,
                                           @Param("districtCode") String districtCode,
                                           @Param("subdistrictCode") String subdistrictCode,
                                           @Param("createdBy") String createdBy,
                                           Pageable pageable);
}
