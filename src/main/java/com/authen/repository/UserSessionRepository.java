package com.authen.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.authen.entity.UserSession;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface UserSessionRepository extends JpaRepository<UserSession, Integer> {

    // user is fetched with the session: validateToken reads is_active outside any transaction
    @EntityGraph(attributePaths = "user")
    Optional<UserSession> findByToken(String token);

    // one session per account: a new log-in ends every other active session of the user. The rows stay
    // (until expired + session-cleanup) so the old device is answered "SESSION_REPLACED", not "expired"
    @Transactional
    @Modifying
    @Query("update UserSession s set s.endedReason = :reason where s.user.userId = :userId and s.endedReason is null")
    int endActiveSessions(@Param("userId") Integer userId, @Param("reason") String reason);

    // expired sessions are otherwise deleted only when their token is used again
    @Transactional
    @Modifying
    @Query("delete from UserSession s where s.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") LocalDateTime cutoff);
}
