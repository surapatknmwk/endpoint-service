package com.core.service.notification.line;

/** Published when a notification was queued for LINE, so it is sent right after the commit (not at the next minute). */
public record LinePendingEvent() {
}
