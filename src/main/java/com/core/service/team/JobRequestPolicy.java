package com.core.service.team;

import com.core.entity.DeliveryJob;
import com.core.entity.JobDriverRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Time rules for job requests and late starts (driver-risk round 3, ข้อ 16) — values from {@code job-request.*}.
 * <ul>
 *   <li>A request expires at {@code requestedAt + ttlHours} or at {@code cutoffHour}:00 of the job's scheduled day,
 *   whichever comes first — but never sooner than {@code requestedAt + minHours} ({@code scheduled_date} is a date
 *   only, so a same-day request would otherwise expire at once).</li>
 *   <li>A job is late when it is still not started at {@code lateStartHour}:00 of its scheduled day.</li>
 * </ul>
 */
@Component
public class JobRequestPolicy {

    private final int ttlHours;
    private final int cutoffHour;
    private final int minHours;
    private final int lateStartHour;

    public JobRequestPolicy(@Value("${job-request.ttl-hours:24}") int ttlHours,
                            @Value("${job-request.cutoff-hour:8}") int cutoffHour,
                            @Value("${job-request.min-hours:2}") int minHours,
                            @Value("${job-request.late-start-hour:10}") int lateStartHour) {
        this.ttlHours = ttlHours;
        this.cutoffHour = cutoffHour;
        this.minHours = minHours;
        this.lateStartHour = lateStartHour;
    }

    public LocalDateTime expiresAt(JobDriverRequest request, DeliveryJob job) {
        LocalDateTime requestedAt = request.getRequestedAt();
        LocalDateTime expiresAt = requestedAt.plusHours(ttlHours);
        if (job != null && job.getScheduledDate() != null) {
            LocalDateTime cutoff = job.getScheduledDate().toLocalDate().atTime(cutoffHour, 0);
            if (cutoff.isBefore(expiresAt)) {
                expiresAt = cutoff;
            }
        }
        LocalDateTime earliest = requestedAt.plusHours(minHours);
        return expiresAt.isBefore(earliest) ? earliest : expiresAt;
    }

    public boolean isExpired(JobDriverRequest request, DeliveryJob job, LocalDateTime now) {
        return !now.isBefore(expiresAt(request, job));
    }

    /** From when, on {@code day}, a job that has not started counts as late. */
    public LocalDateTime lateFrom(LocalDate day) {
        return day.atTime(lateStartHour, 0);
    }
}
