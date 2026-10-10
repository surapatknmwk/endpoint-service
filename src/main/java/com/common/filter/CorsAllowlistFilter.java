package com.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The only place CORS is decided, for every module (authen / core / master / search). Runs
 * before Spring Security and {@link SecurityFilter}.
 *
 * <p>CORS response headers are sent only to origins listed in {@code security.cors.allowed-origins}.
 * A request from any other origin still goes down the chain without CORS headers, so the webapp
 * keeps working when it is served same-origin through a proxy (Caddy / Vite) that forwards the
 * browser's {@code Origin} header, while a browser on a foreign origin cannot read the response
 * and has its preflight rejected.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorsAllowlistFilter extends OncePerRequestFilter {

    private static final String ALLOWED_METHODS = "GET, POST, PUT, PATCH, DELETE, OPTIONS";
    private static final String ALLOWED_HEADERS = "Authorization, Content-Type";
    private static final String MAX_AGE_SECONDS = "3600";

    private final Set<String> allowedOrigins;

    public CorsAllowlistFilter(@Value("${security.cors.allowed-origins:}") String allowedOrigins) {
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .map(origin -> origin.endsWith("/") ? origin.substring(0, origin.length() - 1) : origin)
                .filter(origin -> !origin.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String origin = request.getHeader(HttpHeaders.ORIGIN);
        boolean originAllowed = origin != null && allowedOrigins.contains(origin);
        boolean preflight = origin != null
                && "OPTIONS".equalsIgnoreCase(request.getMethod())
                && request.getHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD) != null;

        response.addHeader(HttpHeaders.VARY, HttpHeaders.ORIGIN);

        if (originAllowed) {
            response.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin);
            response.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
            response.setHeader(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.AUTHORIZATION);
        }

        if (preflight) {
            if (originAllowed) {
                response.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, ALLOWED_METHODS);
                response.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, ALLOWED_HEADERS);
                response.setHeader(HttpHeaders.ACCESS_CONTROL_MAX_AGE, MAX_AGE_SECONDS);
                response.setStatus(HttpServletResponse.SC_OK);
            } else {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            }
            return;
        }

        filterChain.doFilter(request, response);
    }
}
