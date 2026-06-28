package com.core.repository;

import com.core.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {

    Optional<Order> findByOrderCode(String orderCode);

    boolean existsByOrderCode(String orderCode);

    List<Order> findByCustomerCustomerId(Long customerId);

    List<Order> findByPlatformId(Integer platformId);

    List<Order> findByCustomerCustomerIdAndPlatformId(Long customerId, Integer platformId);

    List<Order> findByOrderStatus(String orderStatus);

    List<Order> findByStatus(String status);
}
