package com.authen.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.authen.entity.UserSession;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface UserSessionRepository extends JpaRepository<UserSession, Integer> {

    Optional<UserSession> findByToken(String token);

    void deleteByExpiresAtBefore(LocalDateTime dateTime);

    void deleteByUser_UserId(Integer userId);

}
