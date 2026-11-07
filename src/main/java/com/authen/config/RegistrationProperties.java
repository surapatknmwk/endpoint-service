package com.authen.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** {@code registration.*} and {@code line.login.*} settings (register.md "Settings"). Restart to change. */
@Getter
@Component
public class RegistrationProperties {

    @Value("${registration.enabled:true}")
    private boolean registrationEnabled;

    @Value("${registration.default-role:NORMAL}")
    private String defaultRole;

    @Value("${registration.terms-version:1}")
    private String termsVersion;

    @Value("${registration.max-per-ip-per-hour:5}")
    private int maxPerIpPerHour;

    @Value("${line.login.enabled:false}")
    private boolean lineEnabled;

    @Value("${line.login.channel-id:}")
    private String channelId;

    @Value("${line.login.channel-secret:}")
    private String channelSecret;

    @Value("${line.login.redirect-uri:}")
    private String redirectUri;

    @Value("${line.login.attempt-ttl-seconds:600}")
    private long attemptTtlSeconds;

    @Value("${line.login.ticket-ttl-seconds:900}")
    private long ticketTtlSeconds;

    // line.messaging.* (also read by com.core.service.notification.line.LineMessagingProperties): the Account
    // page switch and the add-friend prompt at log-in only make sense while LINE notifications are on
    @Value("${line.messaging.enabled:false}")
    private boolean lineMessagingEnabled;

    @Value("${line.messaging.add-friend-url:}")
    private String lineAddFriendUrl;

    @PostConstruct
    void check() {
        if (lineEnabled && (!StringUtils.hasText(channelId) || !StringUtils.hasText(channelSecret)
                || !StringUtils.hasText(redirectUri))) {
            throw new IllegalStateException("line.login.enabled=true needs LINE_CHANNEL_ID, LINE_CHANNEL_SECRET "
                    + "and LINE_REDIRECT_URI");
        }
    }
}
