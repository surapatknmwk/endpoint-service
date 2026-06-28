package com.search.repository;

import com.search.entity.OrderAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface SearchOrderAddressRepository extends JpaRepository<OrderAddress, Long>, JpaSpecificationExecutor<OrderAddress> {
}
