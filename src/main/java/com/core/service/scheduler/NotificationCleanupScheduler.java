package com.core.service.scheduler;

import com.core.service.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Daily: deletes notifications older than the retention period, read or not. */
@Component
@RequiredArgsConstructor
public class NotificationCleanupScheduler {

    private final ScheduledTaskRunner runner;
    private final NotificationService notificationService;

    @Value("${scheduler.notification-cleanup.retention-days:90}")
    private int retentionDays;

    @Scheduled(cron = "${scheduler.notification-cleanup.cron:0 5 3 * * *}", zone = "Asia/Bangkok")
    public void trigger() {
        runner.run("notification-cleanup", () -> notificationService.deleteOlderThan(retentionDays));
    }
}
