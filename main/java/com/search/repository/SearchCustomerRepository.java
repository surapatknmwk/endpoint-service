package com.search.repository;

import com.search.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface SearchCustomerRepository extends JpaRepository<Customer, Long>, JpaSpecificationExecutor<Customer> {
}
