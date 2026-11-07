package com.core.service.scheduler;

import com.core.service.notification.reminder.JobDueReminderService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Hourly by default, so a job scheduled for tomorrow later in the day is still reminded. */
@Component
@RequiredArgsConstructor
public class JobDueReminderScheduler {

    private final ScheduledTaskRunner runner;
    private final JobDueReminderService jobDueReminderService;

    @Scheduled(cron = "${scheduler.job-due-reminder.cron:0 5 * * * *}", zone = "Asia/Bangkok")
    public void trigger() {
        runner.run("job-due-reminder", jobDueReminderService::run);
    }
}
