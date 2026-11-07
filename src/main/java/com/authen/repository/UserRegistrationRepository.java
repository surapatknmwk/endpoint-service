package com.authen.repository;

import com.authen.entity.UserRegistration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface UserRegistrationRepository extends JpaRepository<UserRegistration, Integer> {

    long countByRegisteredIpAndCreatedAtAfter(String registeredIp, LocalDateTime after);
}
