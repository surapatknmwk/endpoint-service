package com.core.service.team;

import com.authen.entity.User;
import com.authen.event.UserLoggedInEvent;
import com.authen.repository.UserRepository;
import com.core.constant.NotificationType;
import com.core.constant.TeamMemberStatus;
import com.core.entity.Notification;
import com.core.entity.TeamMember;
import com.core.entity.UserDevice;
import com.core.repository.TeamMemberRepository;
import com.core.repository.UserDeviceRepository;
import com.core.service.notification.NotificationPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * Remembers the devices an account logs in from, and tells the owners of a driver's ACTIVE teams when the driver logs
 * in from a new device while another one was used within {@link #RECENT} — maybe someone else uses the account
 * (driver-risk ข้อ 15). Best effort: a browser id lives in localStorage, so clearing site data or a private window
 * also looks like a new device. The first device ever, and owners' devices, never alert.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DriverDeviceWatcher {

    static final Duration RECENT = Duration.ofDays(30);
    private static final int USER_AGENT_MAX = 500;

    private final UserDeviceRepository userDeviceRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final UserRepository userRepository;
    private final NotificationPublisher notificationPublisher;
    private final Clock clock;

    @EventListener
    public void onLogin(UserLoggedInEvent event) {
        String deviceId = cleanDeviceId(event.deviceId());
        User user = event.user();
        if (deviceId == null || user == null || user.getUserId() == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        String userAgent = event.userAgent() == null ? null
                : event.userAgent().substring(0, Math.min(event.userAgent().length(), USER_AGENT_MAX));
        UserDevice known = userDeviceRepository.findByUserIdAndDeviceId(user.getUserId(), deviceId).orElse(null);
        if (known != null) {
            known.setLastSeenAt(now);
            known.setLastIp(event.ipAddress());
            known.setUserAgent(userAgent);
            userDeviceRepository.save(known);
            return;
        }
        boolean hadOtherDevice = userDeviceRepository.countByUserIdAndDeviceIdNotAndLastSeenAtAfter(
                user.getUserId(), deviceId, now.minus(RECENT)) > 0;
        UserDevice device = userDeviceRepository.save(UserDevice.builder()
                .userId(user.getUserId()).deviceId(deviceId).deviceLabel(label(userAgent)).userAgent(userAgent)
                .lastIp(event.ipAddress()).firstSeenAt(now).lastSeenAt(now).build());
        if (hadOtherDevice && TeamService.isDriver(user)) {
            tellOwners(user, device);
        }
    }

    private void tellOwners(User driver, UserDevice device) {
        List<TeamMember> teams = teamMemberRepository.findByMemberUserIdAndMemberStatusOrderByCreatedAtDesc(
                driver.getUserId(), TeamMemberStatus.ACTIVE);
        String name = TeamService.toSummary(driver).getDisplayName();
        for (TeamMember team : teams) {
            userRepository.findById(team.getOwnerUserId()).ifPresent(owner -> notificationPublisher.publish(
                    Notification.builder()
                            .recipientUsername(owner.getUsername())
                            .notificationType(NotificationType.DRIVER_NEW_DEVICE)
                            .title("คนส่งเข้าสู่ระบบจากเครื่องใหม่")
                            .message(name + " เข้าสู่ระบบจาก " + device.getDeviceLabel()
                                    + " · ถ้าไม่ใช่เครื่องของเขา อาจมีคนอื่นใช้บัญชีนี้")
                            .dedupeKey(NotificationType.DRIVER_NEW_DEVICE + ":" + device.getUserDeviceId())
                            .build()));
        }
        log.info("Driver {} logged in from a new device {} — told {} owner(s)", driver.getUsername(),
                device.getUserDeviceId(), teams.size());
    }

    /** Letters, digits and dashes only, 8–64 long; anything else is treated as no id. */
    static String cleanDeviceId(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String id = raw.trim();
        return id.matches("[A-Za-z0-9-]{8,64}") ? id : null;
    }

    /** "iPhone · LINE" / "Android · Chrome" / "Windows · Edge" — a rough label for people. */
    static String label(String userAgent) {
        if (userAgent == null) {
            return "เครื่องที่ไม่รู้จัก";
        }
        String ua = userAgent.toLowerCase(Locale.ROOT);
        String device = ua.contains("iphone") ? "iPhone" : ua.contains("ipad") ? "iPad"
                : ua.contains("android") ? "Android" : ua.contains("windows") ? "Windows"
                : ua.contains("mac os") ? "Mac" : ua.contains("linux") ? "Linux" : "เครื่องอื่น";
        String browser = ua.contains(" line/") ? "LINE" : ua.contains("edg/") ? "Edge"
                : ua.contains("samsungbrowser") ? "Samsung Internet" : ua.contains("firefox") || ua.contains("fxios")
                ? "Firefox" : ua.contains("chrome") || ua.contains("crios") ? "Chrome"
                : ua.contains("safari") ? "Safari" : "เบราว์เซอร์อื่น";
        return device + " · " + browser;
    }
}
