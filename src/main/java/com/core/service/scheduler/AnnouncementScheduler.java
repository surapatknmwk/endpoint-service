package com.core.service.scheduler;

import com.core.service.notification.announcement.AnnouncementService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Every 5 minutes by default, so an announcement reaches the bell at most ~5 minutes after its publish_at. */
@Component
@RequiredArgsConstructor
public class AnnouncementScheduler {

    private final ScheduledTaskRunner runner;
    private final AnnouncementService announcementService;

    @Scheduled(cron = "${scheduler.announcement.cron:0 */5 * * * *}", zone = "Asia/Bangkok")
    public void trigger() {
        runner.run("announcement", announcementService::run);
    }
}
