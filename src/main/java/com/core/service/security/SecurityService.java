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
}
