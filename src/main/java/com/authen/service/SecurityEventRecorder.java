package com.authen.service;

import com.authen.constant.SecurityEventType;
import com.authen.entity.SecurityEvent;
import com.authen.repository.SecurityEventRepository;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Collects risky activity for the {@code security_events} table without ever touching the
 * database on the request thread: {@link #record} only updates an in-memory aggregate, and
 * {@link #flush} writes one row per distinct event every flush interval. So a flood of
 * rejected calls costs one row per interval, not one row (or query) per call.
 */
@Slf4j
@Service
public class SecurityEventRecorder {

    /** Distinct events held between flushes; beyond this new ones are dropped (and counted). */
    static final int MAX_PENDING = 5_000;

    private final SecurityEventRepository repository;
    private final Clock clock;

    private final ConcurrentHashMap<String, Pending> pending = new ConcurrentHashMap<>();
    private final AtomicLong dropped = new AtomicLong();

    @Autowired
    public SecurityEventRecorder(SecurityEventRepository repository) {
        this(repository, Clock.systemDefaultZone());
    }

    SecurityEventRecorder(SecurityEventRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * @param request  the call that triggered the event; its method/path are stored
     * @param username who did it, when known (may be null)
     * @param detail   free text for whoever reads the row (may be null) — never a password or token
     */
    public void record(SecurityEventType type, HttpServletRequest request, String username, String detail) {
        record(type, clientIp(request), username, request.getMethod(), request.getRequestURI(),
                request.getHeader("User-Agent"), detail);
    }

    /** For events that are about a client rather than one endpoint (no method/path). */
    public void record(SecurityEventType type, String ipAddress, String username, String method, String path,
                       String userAgent, String detail) {
        LocalDateTime now = LocalDateTime.now(clock);
        String key = type + "|" + ipAddress + "|" + username + "|" + method + "|" + path;

        if (pending.size() >= MAX_PENDING && !pending.containsKey(key)) {
            dropped.incrementAndGet();
            return;
        }

        pending.compute(key, (k, existing) -> {
            if (existing == null) {
                return new Pending(type, ipAddress, username, method, path, userAgent, detail, now);
            }
            existing.count++;
            existing.lastSeenAt = now;
            return existing;
        });
    }

    @Scheduled(fixedDelayString = "${security.events.flush-seconds:30}", timeUnit = TimeUnit.SECONDS)
    public void flush() {
        List<SecurityEvent> rows = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now(clock);

        for (String key : List.copyOf(pending.keySet())) {
            Pending event = pending.remove(key);
            if (event != null && event.count >= event.type.getMinOccurrences()) {
                rows.add(event.toEntity(now));
            }
        }

        long droppedCount = dropped.getAndSet(0);
        if (droppedCount > 0) {
            log.warn("Security events dropped (more than {} distinct events in one interval): {}", MAX_PENDING, droppedCount);
        }
        if (rows.isEmpty()) {
            return;
        }

        try {
            repository.saveAll(rows);
        } catch (Exception e) {
            // Detection must never take the service down; the events of this interval are lost.
            log.error("Could not write {} security event(s) -- does table security_events exist "
                    + "(db/alter-add-security-events.sql)? {}", rows.size(), e.getMessage());
        }
    }

    @PreDestroy
    void flushOnShutdown() {
        flush();
    }

    /**
     * The reverse proxy (Caddy) overwrites X-Forwarded-For with the real client address, and in
     * prod the service only listens on loopback, so the header can be trusted there.
     */
    public static String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static String truncate(String value, int maxLength) {
        return value != null && value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    /** Mutated only inside {@code pending.compute}, read only after {@code pending.remove}. */
    private static final class Pending {
        private final SecurityEventType type;
        private final String ipAddress;
        private final String username;
        private final String method;
        private final String path;
        private final String userAgent;
        private final String detail;
        private final LocalDateTime firstSeenAt;
        private LocalDateTime lastSeenAt;
        private int count = 1;

        private Pending(SecurityEventType type, String ipAddress, String username, String method, String path,
                        String userAgent, String detail, LocalDateTime now) {
            this.type = type;
            this.ipAddress = ipAddress;
            this.username = username;
            this.method = method;
            this.path = path;
            this.userAgent = userAgent;
            this.detail = detail;
            this.firstSeenAt = now;
            this.lastSeenAt = now;
        }

        private SecurityEvent toEntity(LocalDateTime createdAt) {
            SecurityEvent entity = new SecurityEvent();
            entity.setEventType(type.name());
            entity.setSeverity(type.getSeverity().name());
            entity.setUsername(truncate(username, 50));
            entity.setIpAddress(truncate(ipAddress, 45));
            entity.setHttpMethod(truncate(method, 10));
            entity.setRequestPath(truncate(path, 500));
            entity.setUserAgent(truncate(userAgent, 500));
            entity.setDetail(detail);
            entity.setOccurrenceCount(count);
            entity.setFirstSeenAt(firstSeenAt);
            entity.setLastSeenAt(lastSeenAt);
            entity.setCreatedAt(createdAt);
            return entity;
        }
    }
}
