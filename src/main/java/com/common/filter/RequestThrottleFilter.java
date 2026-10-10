package com.common.filter;

import com.authen.constant.SecurityEventType;
import com.authen.service.SecurityEventRecorder;
import com.common.dto.ApiResponse;
import com.core.service.security.SecurityService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Rejects repeated calls with {@code 429}, before Spring Security and {@link SecurityFilter}
 * (so a rejected call costs no DB work). Two independent guards, both kept in memory:
 *
 * <ul>
 *   <li><b>Flood guard</b> — every module: at most {@code max-requests} calls per client IP in
 *       a {@code window-seconds} window (the window starts at that IP's first call).</li>
 *   <li><b>Double-submit guard</b> — core / master / search writes (PUT, PATCH, DELETE, and POST
 *       unless listed in {@code duplicate-exempt-posts}): the same token + method + URL is
 *       accepted once per {@code duplicate-window-seconds}. The first call that ends in an
 *       error (status &gt;= 400) releases the lock so the operator can fix and resubmit.</li>
 * </ul>
 *
 * Counters live in this JVM only — correct for the single-instance deployment.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestThrottleFilter extends OncePerRequestFilter {

    static final String TOO_MANY_REQUESTS_MESSAGE = "มีการเรียกใช้งานถี่เกินไป กรุณารอสักครู่แล้วลองใหม่";
    static final String DUPLICATE_REQUEST_MESSAGE = "ส่งคำสั่งซ้ำเร็วเกินไป กรุณารอสักครู่แล้วลองใหม่";

    private static final List<String> ALL_PREFIXES = List.of(
            "/endpoint-authen-service", "/endpoint-core-service", "/endpoint-master-service", "/endpoint-search-service");
    private static final List<String> DUPLICATE_GUARD_PREFIXES = List.of(
            "/endpoint-core-service", "/endpoint-master-service", "/endpoint-search-service");
    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");
    private static final int MAX_TRACKED_KEYS = 100_000;

    private final boolean enabled;
    private final int windowSeconds;
    private final int maxRequests;
    private final int duplicateWindowSeconds;
    private final List<String> duplicateExemptPosts;
    private final SecurityEventRecorder securityEventRecorder;
    private final SecurityService securityService;

    private final Cache<String, AtomicInteger> requestCounts;
    private final Cache<String, Boolean> recentWrites;

    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Autowired
    public RequestThrottleFilter(
            @Value("${security.throttle.enabled:true}") boolean enabled,
            @Value("${security.throttle.window-seconds:10}") int windowSeconds,
            @Value("${security.throttle.max-requests:200}") int maxRequests,
            @Value("${security.throttle.duplicate-window-seconds:3}") int duplicateWindowSeconds,
            @Value("${security.throttle.duplicate-exempt-posts:}") String duplicateExemptPosts,
            SecurityEventRecorder securityEventRecorder,
            SecurityService securityService) {
        this(enabled, windowSeconds, maxRequests, duplicateWindowSeconds, duplicateExemptPosts,
                securityEventRecorder, securityService, Ticker.systemTicker());
    }

    RequestThrottleFilter(boolean enabled, int windowSeconds, int maxRequests, int duplicateWindowSeconds,
                          String duplicateExemptPosts, SecurityEventRecorder securityEventRecorder,
                          SecurityService securityService, Ticker ticker) {
        this.securityEventRecorder = securityEventRecorder;
        this.securityService = securityService;
        this.enabled = enabled;
        this.windowSeconds = windowSeconds;
        this.maxRequests = maxRequests;
        this.duplicateWindowSeconds = duplicateWindowSeconds;
        this.duplicateExemptPosts = Arrays.stream(duplicateExemptPosts.split(","))
                .map(String::trim)
                .filter(pattern -> !pattern.isEmpty())
                .toList();
        this.requestCounts = Caffeine.newBuilder()
                .maximumSize(MAX_TRACKED_KEYS)
                .expireAfterWrite(windowSeconds, TimeUnit.SECONDS)
                .ticker(ticker)
                .build();
        this.recentWrites = Caffeine.newBuilder()
                .maximumSize(MAX_TRACKED_KEYS)
                .expireAfterWrite(duplicateWindowSeconds, TimeUnit.SECONDS)
                .ticker(ticker)
                .build();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !enabled
                || "OPTIONS".equalsIgnoreCase(request.getMethod())
                || ALL_PREFIXES.stream().noneMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String clientIp = SecurityEventRecorder.clientIp(request);

        int count = requestCounts.get(clientIp, ip -> new AtomicInteger()).incrementAndGet();
        if (count > maxRequests) {
            // one line per window per IP is enough to see a flood without flooding the log
            if (count == maxRequests + 1) {
                log.warn("Throttled: ip={} exceeded {} requests in {}s", clientIp, maxRequests, windowSeconds);
            }
            // keyed by IP only (no method/path), so a flood is one row per flush interval
            securityEventRecorder.record(SecurityEventType.RATE_LIMIT_EXCEEDED, clientIp, null, null, null,
                    request.getHeader(HttpHeaders.USER_AGENT),
                    "More than " + maxRequests + " requests in " + windowSeconds + "s");
            reject(response, windowSeconds, TOO_MANY_REQUESTS_MESSAGE);
            return;
        }

        if (!isGuardedWrite(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String writeKey = writeKey(request, clientIp);
        if (recentWrites.asMap().putIfAbsent(writeKey, Boolean.TRUE) != null) {
            log.warn("Duplicate write rejected: ip={} {} {}", clientIp, request.getMethod(), request.getRequestURI());
            // the token has not been verified yet at this point, so the username is as claimed
            String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
            securityEventRecorder.record(SecurityEventType.DUPLICATE_REQUEST, request,
                    authHeader != null ? securityService.extractUsernameFromToken(authHeader) : null,
                    "Same write repeated within " + duplicateWindowSeconds + "s");
            reject(response, duplicateWindowSeconds, DUPLICATE_REQUEST_MESSAGE);
            return;
        }

        boolean succeeded = false;
        try {
            filterChain.doFilter(request, response);
            succeeded = response.getStatus() < 400;
        } finally {
            if (!succeeded) {
                recentWrites.invalidate(writeKey);
            }
        }
    }

    private boolean isGuardedWrite(HttpServletRequest request) {
        String method = request.getMethod().toUpperCase();
        String path = request.getRequestURI();
        if (!WRITE_METHODS.contains(method) || DUPLICATE_GUARD_PREFIXES.stream().noneMatch(path::startsWith)) {
            return false;
        }
        return !("POST".equals(method)
                && duplicateExemptPosts.stream().anyMatch(pattern -> pathMatcher.match(pattern, path)));
    }

    /** Same caller (token, or IP when anonymous) + method + full URL. The token is hashed, never stored. */
    private String writeKey(HttpServletRequest request, String clientIp) {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        String caller = authHeader != null ? sha256(authHeader) : clientIp;
        String query = request.getQueryString();
        return caller + ' ' + request.getMethod().toUpperCase() + ' ' + request.getRequestURI()
                + (query != null ? '?' + query : "");
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private void reject(HttpServletResponse response, int retryAfterSeconds, String message) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        ApiResponse<Object> body = ApiResponse.error(HttpStatus.TOO_MANY_REQUESTS.value(), message);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
