package com.core.repository;

import com.core.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long>, JpaSpecificationExecutor<Customer> {

    Optional<Customer> findByPhone(String phone);

    Optional<Customer> findByEmail(String email);

    List<Customer> findByStatus(String status);

    List<Customer> findByNameContainingIgnoreCase(String name);

    List<Customer> findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase(String firstName, String lastName);

    boolean existsByPhone(String phone);

    boolean existsByEmail(String email);
}
