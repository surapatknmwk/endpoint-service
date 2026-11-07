package com.core.service.notification.line;

import com.authen.entity.UserIdentity;
import com.authen.repository.UserIdentityRepository;
import com.authen.repository.UserRepository;
import com.core.entity.Notification;
import com.core.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

/**
 * ส่งแจ้งเตือนที่รอส่ง LINE (line_status = PENDING) เข้าแชท LINE ของผู้รับ — design lineNotify §6.3
 * แต่ละแถวบันทึกผลทันที (ไม่มี transaction ครอบทั้งรอบ) เพื่อให้ส่งไปแล้วไม่ถูกส่งซ้ำถ้ารอบนี้พังกลางทาง
 * เรียกจาก 2 ที่ (หลัง commit = {@link LineDeliveryTrigger}, ทุกนาที = scheduler) — รันทีละรอบ
 * ถ้ามีรอบอื่นกำลังส่งอยู่ รอบนั้นจะวนอีกครั้งให้ จึงไม่ส่งแถวเดียวกันซ้อนกัน และไม่ตกหล่นแถวที่เพิ่งเข้าคิว
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LineDeliveryService {

    static final String PENDING = "PENDING";
    static final String SENT = "SENT";
    static final String SKIPPED = "SKIPPED";
    static final String FAILED = "FAILED";

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final UserIdentityRepository userIdentityRepository;
    private final LineMessagingClient lineMessagingClient;
    private final LineMessagingProperties properties;
    private final Clock clock;

    private final ReentrantLock running = new ReentrantLock();
    private volatile boolean runAgain;

    public Result run() {
        runAgain = true;
        if (!running.tryLock()) {
            return new Result(0, 0, 0, 0); // the running round sees runAgain and loops once more
        }
        try {
            Result total = new Result(0, 0, 0, 0);
            while (runAgain) {
                runAgain = false;
                total = total.plus(runOnce());
            }
            return total;
        } finally {
            running.unlock();
        }
    }

    private Result runOnce() {
        LocalDateTime now = LocalDateTime.now(clock);
        if (!properties.isEnabled() || properties.isQuiet(now.toLocalTime())) {
            return new Result(0, 0, 0, 0); // ช่วงห้ามส่ง: เก็บไว้ รอบแรกหลังพ้นช่วงส่งให้
        }

        List<Notification> rows = notificationRepository.findLinePending(PageRequest.of(0, properties.getBatchSize()));
        Result result = new Result(0, 0, 0, 0);
        for (Notification row : rows) {
            String status = deliver(row, now);
            result = result.add(status);
            if (status == null || PENDING.equals(status)) {
                break; // LINE ตอบ 429 / ล่ม — หยุดรอบนี้ ไม่เผาจำนวนครั้งที่ลองใหม่ของแถวอื่น รอบถัดไปค่อยลอง
            }
        }
        if (!rows.isEmpty()) {
            log.info("LINE delivery: {} pending, {} sent, {} skipped, {} failed, {} retry later",
                    rows.size(), result.sent(), result.skipped(), result.failed(), result.retry());
        }
        return result;
    }

    /** @return the status written, PENDING when it will be tried again, null when LINE rate-limited us */
    private String deliver(Notification row, LocalDateTime now) {
        int attempts = row.getLineAttempts() == null ? 0 : row.getLineAttempts();
        if (row.getCreatedAt().isBefore(now.minusHours(properties.getMaxAgeHours()))) {
            return mark(row, SKIPPED, attempts, null, "expired");
        }
        if (row.getReadAt() != null) {
            return mark(row, SKIPPED, attempts, null, "read");
        }
        Optional<UserIdentity> identity = userRepository.findByUsername(row.getRecipientUsername())
                .flatMap(user -> userIdentityRepository.findByUserIdAndProvider(user.getUserId(), UserIdentity.PROVIDER_LINE));
        if (identity.isEmpty()) {
            return mark(row, SKIPPED, attempts, null, "not linked");
        }
        if (!identity.get().isNotifyEnabled()) {
            return mark(row, SKIPPED, attempts, null, "off");
        }

        LineMessagingClient.PushResult push = lineMessagingClient.push(identity.get().getProviderUserId(),
                LineFlexMessageBuilder.build(row.getTitle(), row.getMessage(),
                        LineFlexMessageBuilder.openUrl(properties.getWebappBaseUrl(), row.getNotificationId())),
                retryKey(row.getNotificationId()));
        return switch (push.outcome()) {
            case SENT -> mark(row, SENT, attempts + 1, now, null);
            case REJECTED -> mark(row, FAILED, attempts + 1, null, push.error());
            case RETRY -> mark(row, attempts + 1 >= properties.getMaxAttempts() ? FAILED : PENDING, attempts + 1, null,
                    push.error());
            case RATE_LIMITED -> {
                log.warn("LINE delivery stopped: {}", push.error());
                yield null;
            }
        };
    }

    private String mark(Notification row, String status, int attempts, LocalDateTime sentAt, String error) {
        notificationRepository.markLine(row.getNotificationId(), status, attempts, sentAt,
                error == null ? null : error.substring(0, Math.min(error.length(), 200)));
        return status;
    }

    /** Same key on every retry of one notification, so LINE never delivers it twice */
    static UUID retryKey(Long notificationId) {
        return UUID.nameUUIDFromBytes(("notification-" + notificationId).getBytes(StandardCharsets.UTF_8));
    }

    public record Result(int sent, int skipped, int failed, int retry) {
        Result plus(Result other) {
            return new Result(sent + other.sent, skipped + other.skipped, failed + other.failed, retry + other.retry);
        }

        Result add(String status) {
            if (status == null) {
                return this;
            }
            return switch (status) {
                case SENT -> new Result(sent + 1, skipped, failed, retry);
                case SKIPPED -> new Result(sent, skipped + 1, failed, retry);
                case FAILED -> new Result(sent, skipped, failed + 1, retry);
                default -> new Result(sent, skipped, failed, retry + 1);
            };
        }
    }
}
