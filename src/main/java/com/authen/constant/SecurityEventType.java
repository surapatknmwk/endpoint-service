package com.authen.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Kinds of risky activity written to {@code security_events}. The table stores the name as
 * free text, so adding a constant here needs no database change.
 */
@Getter
@RequiredArgsConstructor
public enum SecurityEventType {

    /** One client IP went over the request limit (RequestThrottleFilter flood guard). */
    RATE_LIMIT_EXCEEDED(Severity.HIGH, 1),

    /**
     * The same write was repeated inside the double-submit window. A single repeat is an
     * ordinary double click, so it is only stored from the third rejection on.
     */
    DUPLICATE_REQUEST(Severity.LOW, 3),

    /** Wrong password, unknown username, or disabled account at login. */
    LOGIN_FAILED(Severity.MEDIUM, 1),

    /** A valid token called an endpoint its role has no permission for. */
    PERMISSION_DENIED(Severity.MEDIUM, 1);

    public enum Severity { LOW, MEDIUM, HIGH }

    private final Severity severity;

    /** Occurrences needed within one flush interval before the event is worth storing. */
    private final int minOccurrences;
}
