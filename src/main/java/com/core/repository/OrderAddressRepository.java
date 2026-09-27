package com.core.repository;

import com.core.entity.OrderAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;


@Repository
public interface OrderAddressRepository extends JpaRepository<OrderAddress, Long>, JpaSpecificationExecutor<OrderAddress> {
}
