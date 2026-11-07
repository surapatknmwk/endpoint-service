package com.core.service.notification.line;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Sends queued LINE notifications right after the transaction that created them commits, on one background thread,
 * so the action that caused them (accept a job, invite …) does not wait for LINE. A rolled-back action queues nothing.
 * Many notifications in one transaction (or in a burst) cause one run, not one per notification.
 * {@link com.core.service.scheduler.LineDeliveryScheduler} still runs every minute for retries and quiet hours.
 */
@Slf4j
@Component
public class LineDeliveryTrigger {

    private final LineDeliveryService lineDeliveryService;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "line-delivery");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean queued = new AtomicBoolean(false);

    public LineDeliveryTrigger(LineDeliveryService lineDeliveryService) {
        this.lineDeliveryService = lineDeliveryService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPending(LinePendingEvent event) {
        if (!queued.compareAndSet(false, true)) {
            return; // a run is already waiting and will pick this one up
        }
        executor.execute(() -> {
            queued.set(false); // rows queued from now on need another run
            try {
                lineDeliveryService.run();
            } catch (RuntimeException e) {
                log.error("LINE delivery after commit failed; the scheduled run will retry", e);
            }
        });
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }
}
