package com.core.repository;

import com.core.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {

    @Query("SELECT o FROM Order o WHERE o.status = 'A' "
            + "AND UPPER(o.orderStatus) IN ('NEW', 'PENDING') "
            + "AND o.address.provinceCode IN :provinceCodes "
            + "AND (:districtCode IS NULL OR o.address.districtCode = :districtCode) "
            + "AND (:subdistrictCode IS NULL OR o.address.subdistrictCode = :subdistrictCode)")
    List<Order> findCandidateOrdersForJob(@Param("provinceCodes") Collection<String> provinceCodes,
                                           @Param("districtCode") String districtCode,
                                           @Param("subdistrictCode") String subdistrictCode);
}
