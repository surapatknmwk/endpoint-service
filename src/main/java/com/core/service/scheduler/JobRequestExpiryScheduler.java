package com.core.service.scheduler;

import com.core.service.team.JobDriverRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Every 10 minutes by default: job requests nobody answered in time → EXPIRED (driver-risk ข้อ 16). */
@Component
@RequiredArgsConstructor
public class JobRequestExpiryScheduler {

    private final ScheduledTaskRunner runner;
    private final JobDriverRequestService jobDriverRequestService;

    @Scheduled(cron = "${scheduler.job-request-expiry.cron:0 */10 * * * *}", zone = "Asia/Bangkok")
    public void trigger() {
        runner.run("job-request-expiry", jobDriverRequestService::expireOverdue);
    }
}
