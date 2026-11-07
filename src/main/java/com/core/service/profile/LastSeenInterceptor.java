package com.core.service.profile;

import com.authen.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Every core request with a valid token counts as "using the app". Runs in every profile (the permission filter
 * is on in prod only); the token signature and expiry are checked here, so a forged header is ignored.
 */
@Component
@RequiredArgsConstructor
public class LastSeenInterceptor implements HandlerInterceptor {

    private static final String BEARER = "Bearer ";

    private final JwtUtil jwtUtil;
    private final LastSeenRecorder lastSeenRecorder;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER)) {
            try {
                lastSeenRecorder.record(jwtUtil.extractUsername(header.substring(BEARER.length())));
            } catch (RuntimeException ignored) {
                // invalid or expired token — the request itself is handled (and refused) elsewhere
            }
        }
        return true;
    }
}
