package com.core.service.notification.line;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalTime;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/** {@code line.messaging.*} settings (docs/task/enhanment/lineNotify/design.md §6.1). Restart to change. */
@Getter
@Component
public class LineMessagingProperties {

    @Value("${line.messaging.enabled:false}")
    private boolean enabled;

    @Value("${line.messaging.channel-access-token:}")
    private String channelAccessToken;

    @Value("${line.messaging.add-friend-url:}")
    private String addFriendUrl;

    @Value("${line.messaging.webapp-base-url:}")
    private String webappBaseUrl;

    @Value("${line.messaging.types:}")
    private String typesValue;

    @Value("${line.messaging.quiet-from:21:00}")
    private LocalTime quietFrom;

    @Value("${line.messaging.quiet-to:07:00}")
    private LocalTime quietTo;

    @Value("${line.messaging.max-attempts:3}")
    private int maxAttempts;

    @Value("${line.messaging.max-age-hours:24}")
    private int maxAgeHours;

    @Value("${line.messaging.batch-size:50}")
    private int batchSize;

    private Set<String> types = Set.of();

    @PostConstruct
    void init() {
        types = Arrays.stream(typesValue.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toUnmodifiableSet());
        if (enabled && (!StringUtils.hasText(channelAccessToken) || !StringUtils.hasText(webappBaseUrl))) {
            throw new IllegalStateException("line.messaging.enabled=true needs LINE_MESSAGING_TOKEN and WEBAPP_BASE_URL");
        }
    }

    /** true = a notification of this type is queued for LINE when it is created */
    public boolean queues(String notificationType) {
        return enabled && types.contains(notificationType);
    }

    /** 21:00–07:00 by default; the window may cross midnight; from == to means no quiet hours */
    public boolean isQuiet(LocalTime time) {
        if (quietFrom.equals(quietTo)) {
            return false;
        }
        if (quietFrom.isBefore(quietTo)) {
            return !time.isBefore(quietFrom) && time.isBefore(quietTo);
        }
        return !time.isBefore(quietFrom) || time.isBefore(quietTo);
    }
}
