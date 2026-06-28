package com.common.filter;

import com.authen.service.AuthenticationService;
import com.common.dto.ApiResponse;
import com.common.exception.InvalidTokenException;
import com.common.exception.TokenExpiredException;
import com.core.service.security.SecurityService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Single security filter covering all three consolidated path prefixes
 * (core / master / search), replacing the 3 near-identical per-module copies.
 * Each module's distinguishing behaviour (core's permission check, search's
 * "sub" claim extraction) is preserved via path-prefix branching below.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityFilter extends OncePerRequestFilter {

    private static final String CORE_PREFIX = "/endpoint-core-service";
    private static final String MASTER_PREFIX = "/endpoint-master-service";
    private static final String SEARCH_PREFIX = "/endpoint-search-service";

    private static final List<String> EXCLUDED_PATHS = List.of(
            "/swagger-ui",
            "/v3/api-docs",
            "/swagger-resources",
            "/h2-console",
            "/actuator",
            "/error"
    );

    private final AuthenticationService authenticationService;
    private final SecurityService securityService;
    private final RequestMappingHandlerMapping requestMappingHandlerMapping;

    @Value("${security.filter.enabled:false}")
    private boolean filterEnabled;

    @Value("${security.filter.token.header:Authorization}")
    private String tokenHeader;

    @Value("${security.filter.token.prefix:Bearer}")
    private String tokenPrefix;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        response.setHeader("Access-Control-Allow-Origin", request.getHeader("Origin"));
        response.setHeader("Access-Control-Allow-Methods", corsAllowedMethods(path));
        response.setHeader("Access-Control-Allow-Headers", "*");
        response.setHeader("Access-Control-Expose-Headers", "Authorization");
        response.setHeader("Access-Control-Allow-Credentials", "true");

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_OK);
            return;
        }

        log.info("=============== [{}] {}", request.getMethod(), path);

        if (isExcludedPath(path) || !filterEnabled) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            validateToken(request, path);
            filterChain.doFilter(request, response);
        } catch (TokenExpiredException e) {
            sendErrorResponse(response, HttpStatus.UNAUTHORIZED, e.getMessage());
        } catch (InvalidTokenException e) {
            sendErrorResponse(response, HttpStatus.UNAUTHORIZED, e.getMessage());
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !(path.startsWith(CORE_PREFIX) || path.startsWith(MASTER_PREFIX) || path.startsWith(SEARCH_PREFIX));
    }

    private String corsAllowedMethods(String path) {
        return path.startsWith(CORE_PREFIX) ? "GET, POST, DELETE, PUT, PATCH, OPTIONS" : "GET, POST";
    }

    private boolean isExcludedPath(String path) {
        return EXCLUDED_PATHS.stream().anyMatch(path::contains);
    }

    private void validateToken(HttpServletRequest request, String path) {
        String authHeader = request.getHeader(tokenHeader);

        if (authHeader == null || !authHeader.startsWith(tokenPrefix + " ")) {
            throw new InvalidTokenException("Missing or invalid Authorization header");
        }

        String token = authHeader.substring((tokenPrefix + " ").length());

        if (token.isEmpty()) {
            throw new InvalidTokenException("Token is empty");
        }

        validateTokenLocally(token);

        if (path.startsWith(CORE_PREFIX)) {
            // Controller mappings already carry the "/endpoint-core-service" prefix in the
            // consolidated application, so the matched pattern is used as-is.
            String apiPattern = getApiPattern(request);
            var checkPermission = securityService.checkPermission(authHeader, request.getMethod(), apiPattern);
            if (checkPermission != null) {
                throw new InvalidTokenException(checkPermission);
            }
        } else if (path.startsWith(SEARCH_PREFIX)) {
            String sub = extractSubFromJwt(token);
            if (sub != null) {
                request.setAttribute("currentUserId", sub);
            }
        }
    }

    private void validateTokenLocally(String token) {
        try {
            if (!authenticationService.validateToken(token)) {
                throw new TokenExpiredException("Token is invalid or expired");
            }
        } catch (TokenExpiredException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error validating token: {}", e.getMessage());
            throw new InvalidTokenException("Token validation failed");
        }
    }

    private String extractSubFromJwt(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) return null;
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
            Map<?, ?> claims = objectMapper.readValue(payload, Map.class);
            Object sub = claims.get("sub");
            return sub != null ? sub.toString() : null;
        } catch (Exception e) {
            log.warn("Could not extract sub from JWT: {}", e.getMessage());
            return null;
        }
    }

    private String getApiPattern(HttpServletRequest request) {
        try {
            requestMappingHandlerMapping.getHandler(request);
        } catch (Exception e) {
            log.warn("Could not resolve handler for: {}", request.getRequestURI());
        }
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (pattern != null) {
            return pattern.toString();
        }
        // fallback: strip context path so permission check uses relative path
        String contextPath = request.getContextPath();
        String uri = request.getRequestURI();
        return contextPath.isEmpty() ? uri : uri.substring(contextPath.length());
    }

    private void sendErrorResponse(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ApiResponse<Object> errorResponse = ApiResponse.error(status.value(), message);
        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }
}
