package com.core.service.scheduler;

import com.authen.repository.UserSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Daily: deletes {@code user_sessions} rows that have expired. Without it a row is removed only when
 * its old token is sent again, so sessions of closed browsers stay in the table forever.
 * Uses the same clock as {@code AuthenticationService} (system time), which writes expires_at.
 */
@Component
@RequiredArgsConstructor
public class SessionCleanupScheduler {

    private final ScheduledTaskRunner runner;
    private final UserSessionRepository userSessionRepository;

    @Scheduled(cron = "${scheduler.session-cleanup.cron:0 10 3 * * *}", zone = "Asia/Bangkok")
    public void trigger() {
        runner.run("session-cleanup", () -> userSessionRepository.deleteExpiredBefore(LocalDateTime.now()));
    }
}
