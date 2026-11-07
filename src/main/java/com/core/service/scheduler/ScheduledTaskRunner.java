package com.core.service.scheduler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Shared wrapper for every scheduled task: logs how long it took, and keeps a failing run from
 * propagating (Spring would only log it anyway) so the next run happens as scheduled.
 */
@Slf4j
@Component
public class ScheduledTaskRunner {

    public void run(String taskName, Runnable task) {
        long start = System.currentTimeMillis();
        try {
            task.run();
            log.info("Scheduled task {} finished in {} ms", taskName, System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.error("Scheduled task {} failed after {} ms", taskName, System.currentTimeMillis() - start, e);
        }
    }
}
