package com.core.service.profile;

import com.authen.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps {@code users.last_seen_at} ("ใช้งานล่าสุด", US-PROF-09) current without a database write per request:
 * at most one write per user every {@link #INTERVAL}, remembered in memory. The SQL repeats the check, so several
 * instances also write rarely. A failure is logged and never reaches the request.
 */
@Slf4j
@Component
public class LastSeenRecorder {

    static final Duration INTERVAL = Duration.ofMinutes(2);

    private final UserRepository userRepository;
    private final Clock clock;
    private final Map<String, Instant> lastWrites = new ConcurrentHashMap<>();

    public LastSeenRecorder(UserRepository userRepository, Clock clock) {
        this.userRepository = userRepository;
        this.clock = clock;
    }

    public void record(String username) {
        if (username == null) {
            return;
        }
        Instant now = clock.instant();
        Instant last = lastWrites.get(username);
        if (last != null && now.isBefore(last.plus(INTERVAL))) {
            return;
        }
        lastWrites.put(username, now);
        try {
            LocalDateTime nowLocal = LocalDateTime.now(clock);
            userRepository.touchLastSeen(username, nowLocal, nowLocal.minus(INTERVAL));
        } catch (RuntimeException e) {
            log.warn("Could not record last seen of {}: {}", username, e.getMessage());
        }
    }
}
