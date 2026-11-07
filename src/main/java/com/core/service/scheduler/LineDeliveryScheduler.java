package com.core.service.scheduler;

import com.core.service.notification.line.LineDeliveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Every minute by default, so a notification reaches LINE about a minute after the bell (outside quiet hours). */
@Component
@RequiredArgsConstructor
public class LineDeliveryScheduler {

    private final ScheduledTaskRunner runner;
    private final LineDeliveryService lineDeliveryService;

    @Scheduled(cron = "${scheduler.line-delivery.cron:0 * * * * *}", zone = "Asia/Bangkok")
    public void trigger() {
        runner.run("line-delivery", lineDeliveryService::run);
    }
}
