package com.search.repository;

import com.search.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface SearchOrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {

    // @EntityGraph(attributePaths = {"customer", "address"})
    Page<Order> findAll(Specification<Order> spec, Pageable pageable);
}
