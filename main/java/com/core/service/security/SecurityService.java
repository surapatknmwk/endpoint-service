package com.core.service.security;

import com.common.config.CacheConfig;
import com.authen.service.AuthenticationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

@Slf4j
@Service
public class SecurityService {

    private final AuthenticationService authenticationService;
    private final ObjectMapper objectMapper;

    @Value("${api.filter.token.prefix:Bearer }")
    private String tokenPrefix;

    public SecurityService(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
        this.objectMapper = new ObjectMapper();
    }

    public String validateToken(String authHeader, String requestUri) {
        // Check if Authorization header exists
        if (authHeader == null || authHeader.isEmpty()) {
            log.warn("Missing Authorization header for request: {}", requestUri);
            return "Missing Authorization header";
        }

        // Check if token has correct prefix
        if (!authHeader.startsWith(tokenPrefix)) {
            log.warn("Invalid token format for request: {}", requestUri);
            return "Invalid token format. Expected: " + tokenPrefix.trim() + " <token>";
        }

        // Extract token
        String token = authHeader.substring(tokenPrefix.length());

        if (token.isEmpty()) {
            log.warn("Empty token for request: {}", requestUri);
            return "Token is empty";
        }

        // Check token expiration
        String expirationError = checkTokenExpiration(token);
        if (expirationError != null) {
            return expirationError;
        }

        log.debug("Token validation passed for request: {}", requestUri);
        return null;
    }

    public String checkPermission(String authHeader, String method, String api) {
        try {
            String role = extractRoleFromToken(authHeader);
            if (role == null) {
                return "Unable to extract role from token";
            }

            boolean hasPermission = checkPermissionCached(role, method, api, authHeader);

            if (hasPermission) {
                log.debug("Permission granted for {} {}", method, api);
                return null;
            } else {
                log.warn("Permission denied for {} {}", method, api);
                return "Access denied. You don't have permission to access this resource.";
            }
        } catch (Exception e) {
            log.error("Error checking permission: {}", e.getMessage());
            return "Unable to verify permission. Please try again later.";
        }
    }


    // Flow การทำงาน:
    // checkPermissionCached("ADMIN", "GET", "/api/users", authHeader)
    //                 │
    //                 ▼
    //         ตรวจสอบ Cache
    //         key = "ADMIN:GET:/api/users"
    //                 │
    //        ┌────────┴────────┐
    //        │                 │
    //       HIT               MISS
    //        │                 │
    //        ▼                 ▼
    //   return ค่าจาก      เข้า method body
    //   cache ทันที        (query permission ใน DB)
    //   ไม่เข้า method          │
    //                          ▼
    //                    เก็บผลลัพธ์ลง cache
    //                          │
    //                          ▼
    //                    return ค่า
    // Request แรกของแต่ละ role+api จะ query permission จาก DB (MISS)
    // Request ถัดไปภายใน 5 นาที จะใช้ cache (HIT) - ไม่ต้อง query ซ้ำ
    @Cacheable(value = CacheConfig.PERMISSION_CACHE, key = "#role + ':' + #method + ':' + #api")
    public boolean checkPermissionCached(String role, String method, String api, String authHeader) {
        log.info("Cache MISS - Checking permission locally: role={}, method={}, api={}", role, method, api);

        try {
            String token = authHeader.substring(tokenPrefix.length());
            return authenticationService.checkPermission(token, method, api);
        } catch (Exception e) {
            log.error("Error checking permission: {}", e.getMessage());
            throw new RuntimeException("Permission check failed", e);
        }
    }

    public String extractUsernameFromToken(String authHeader) {
        try {
            JsonNode payload = parseTokenPayload(authHeader);
            if (payload != null && payload.has("sub")) {
                return payload.get("sub").asText();
            }
            return null;
        } catch (Exception e) {
            log.error("Error extracting username from token: {}", e.getMessage());
            return null;
        }
    }

    public Integer extractUserIdFromToken(String authHeader) {
        try {
            JsonNode payload = parseTokenPayload(authHeader);
            if (payload != null && payload.has("userId")) {
                return payload.get("userId").asInt();
            }
            return null;
        } catch (Exception e) {
            log.error("Error extracting userId from token: {}", e.getMessage());
            return null;
        }
    }

    public String extractRoleFromToken(String authHeader) {
        try {
            JsonNode payload = parseTokenPayload(authHeader);
            if (payload != null && payload.has("role")) {
                return payload.get("role").asText();
            }
            return null;
        } catch (Exception e) {
            log.error("Error extracting role from token: {}", e.getMessage());
            return null;
        }
    }

    private JsonNode parseTokenPayload(String authHeader) {
        try {
            String token = authHeader.substring(tokenPrefix.length());
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return null;
            }

            String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            return objectMapper.readTree(payload);
        } catch (Exception e) {
            log.error("Error parsing token payload: {}", e.getMessage());
            return null;
        }
    }

    private String checkTokenExpiration(String token) {
        try {
            // Simple JWT structure check (header.payload.signature)
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                log.warn("Invalid token structure");
                return "Invalid token structure";
            }

            // Decode payload (second part)
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);

            // Simple expiration check - looking for "exp" claim
            if (payload.contains("\"exp\"")) {
                // Extract exp value (simplified parsing)
                int expIndex = payload.indexOf("\"exp\"");
                int colonIndex = payload.indexOf(":", expIndex);
                int endIndex = payload.indexOf(",", colonIndex);
                if (endIndex == -1) {
                    endIndex = payload.indexOf("}", colonIndex);
                }

                String expValue = payload.substring(colonIndex + 1, endIndex).trim();
                long expTimestamp = Long.parseLong(expValue);
                long currentTimestamp = Instant.now().getEpochSecond();

                if (currentTimestamp > expTimestamp) {
                    log.warn("Token has expired. Exp: {}, Current: {}", expTimestamp, currentTimestamp);
                    return "Token has expired";
                }
            }

            return null;
        } catch (IllegalArgumentException e) {
            log.warn("Failed to decode/parse token: {}", e.getMessage());
            return "Invalid token encoding or format";
        } catch (Exception e) {
            log.warn("Token validation error: {}", e.getMessage());
            return "Token validation failed";
        }
    }
}
