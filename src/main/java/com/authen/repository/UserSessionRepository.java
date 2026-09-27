package com.authen.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.authen.entity.UserSession;

import java.util.Optional;

@Repository
public interface UserSessionRepository extends JpaRepository<UserSession, Integer> {

    Optional<UserSession> findByToken(String token);
}
