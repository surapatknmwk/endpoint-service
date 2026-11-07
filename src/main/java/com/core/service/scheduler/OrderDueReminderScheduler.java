package com.core.service.scheduler;

import com.core.service.notification.reminder.OrderDueReminderService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Once a morning by default: one "order ใกล้ถึงวันนัดส่ง" per owner per day, so orders added later in the day
 * are in the next morning's reminder.
 */
@Component
@RequiredArgsConstructor
public class OrderDueReminderScheduler {

    private final ScheduledTaskRunner runner;
    private final OrderDueReminderService orderDueReminderService;

    @Scheduled(cron = "${scheduler.order-due-reminder.cron:0 0 7 * * *}", zone = "Asia/Bangkok")
    public void trigger() {
        runner.run("order-due-reminder", orderDueReminderService::run);
    }
}
