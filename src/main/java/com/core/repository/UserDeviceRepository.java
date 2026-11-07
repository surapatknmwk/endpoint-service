package com.core.repository;

import com.core.entity.UserDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface UserDeviceRepository extends JpaRepository<UserDevice, Long> {

    Optional<UserDevice> findByUserIdAndDeviceId(Integer userId, String deviceId);

    /** Other devices of the account used since {@code since} — "had another device recently". */
    long countByUserIdAndDeviceIdNotAndLastSeenAtAfter(Integer userId, String deviceId, LocalDateTime since);
}
