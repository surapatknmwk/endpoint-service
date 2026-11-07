package com.core.service.notification;

import com.core.constant.AccountRole;
import com.core.entity.Notification;
import com.core.repository.NotificationRepository;
import com.core.service.notification.line.LineMessagingProperties;
import com.core.service.notification.line.LinePendingEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * The only place that creates notifications. Every new kind of notification goes through here so the
 * dedupe key (one row per recipient + key) is always respected and callers can safely retry.
 * It also decides which notifications are queued for LINE (line_status = PENDING, design lineNotify M1);
 * whether the recipient has linked LINE is checked only when sending.
 */
@Component
@RequiredArgsConstructor
public class NotificationPublisher {

    static final String LINE_PENDING = "PENDING";

    private final NotificationRepository notificationRepository;
    private final LineMessagingProperties lineMessagingProperties;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    /**
     * @return true when the notification was created, false when the recipient already has one with the same
     * dedupe key
     */
    @Transactional
    public boolean publish(Notification draft) {
        String lineStatus = lineMessagingProperties.queues(draft.getNotificationType()) ? LINE_PENDING : null;
        boolean created = notificationRepository.insertIgnore(
                draft.getRecipientUsername(),
                draft.getNotificationType(),
                draft.getTitle(),
                draft.getMessage(),
                draft.getJobId(),
                draft.getTargetDate(),
                draft.getDedupeKey(),
                LocalDateTime.now(clock),
                lineStatus) > 0;
        if (created && lineStatus != null) {
            eventPublisher.publishEvent(new LinePendingEvent()); // send right after commit (LineDeliveryTrigger)
        }
        return created;
    }

    /**
     * Sends the same notification to every active user of an audience ({@code ALL} / {@code OWNER} / {@code DRIVER})
     * in one statement; recipients who already have the dedupe key are skipped.
     *
     * @param sendLine copy to LINE too (per announcement, not by {@code line.messaging.types})
     * @return number of notifications created
     */
    @Transactional
    public int publishToAudience(String audience, String type, String title, String message, String dedupeKey,
                                 boolean sendLine) {
        String lineStatus = sendLine && lineMessagingProperties.isEnabled() ? LINE_PENDING : null;
        int created = notificationRepository.insertForAudience(audience, AccountRole.DRIVER, type, title, message,
                dedupeKey, LocalDateTime.now(clock), lineStatus);
        if (created > 0 && lineStatus != null) {
            eventPublisher.publishEvent(new LinePendingEvent());
        }
        return created;
    }
}
