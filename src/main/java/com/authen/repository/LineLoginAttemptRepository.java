package com.authen.repository;

import com.authen.entity.LineLoginAttempt;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LineLoginAttemptRepository extends JpaRepository<LineLoginAttempt, Long> {

    // locked: a state / ticket is single-use even when two requests arrive together
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<LineLoginAttempt> findByStateHash(String stateHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<LineLoginAttempt> findByTicketHash(String ticketHash);
}
