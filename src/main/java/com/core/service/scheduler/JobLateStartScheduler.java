package com.core.service.scheduler;

import com.core.service.notification.reminder.JobLateStartService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Hourly by default: today's jobs a driver accepted but has not started (driver-risk ข้อ 16). */
@Component
@RequiredArgsConstructor
public class JobLateStartScheduler {

    private final ScheduledTaskRunner runner;
    private final JobLateStartService jobLateStartService;

    @Scheduled(cron = "${scheduler.job-late-start.cron:0 15 * * * *}", zone = "Asia/Bangkok")
    public void trigger() {
        runner.run("job-late-start", jobLateStartService::run);
    }
}
