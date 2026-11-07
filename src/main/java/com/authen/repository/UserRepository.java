package com.authen.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Repository;

import com.authen.entity.User;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Integer> {

    Optional<User> findByUsername(String username);

    // usernames are unique as typed, so "Driver1" and "driver1" can both exist — callers decide what to do with two
    List<User> findByUsernameIgnoreCase(String username);

    List<User> findByUsernameIn(Collection<String> usernames);

    /** Sets last_seen_at = now unless it is already newer than {@code staleBefore}; returns the rows changed. */
    @Modifying
    @Transactional
    @Query(value = "update users set last_seen_at = :now where username = :username "
            + "and (last_seen_at is null or last_seen_at < :staleBefore)", nativeQuery = true)
    int touchLastSeen(@Param("username") String username, @Param("now") LocalDateTime now,
                      @Param("staleBefore") LocalDateTime staleBefore);
}
