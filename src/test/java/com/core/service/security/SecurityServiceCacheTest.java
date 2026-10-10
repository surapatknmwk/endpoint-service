package com.core.service.security;

import com.authen.service.AuthenticationService;
import com.common.config.CacheConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the {@code @Cacheable} on {@link SecurityService#checkPermissionCached} with the real
 * {@link CacheConfig}: the lookup is cached per role + method + api, which only a Spring proxy
 * can prove.
 */
@SpringJUnitConfig(SecurityServiceCacheTest.Config.class)
// the mocked AuthenticationService still carries its @Value field, which Spring tries to fill
@TestPropertySource(properties = "jwt.expiration=3600000")
class SecurityServiceCacheTest {

    @Configuration
    @EnableCaching
    @Import(CacheConfig.class)
    static class Config {
        @Bean
        AuthenticationService authenticationService() {
            return mock(AuthenticationService.class);
        }

        @Bean
        SecurityService securityService(AuthenticationService authenticationService) {
            return new SecurityService(authenticationService);
        }
    }

    @Autowired
    private SecurityService securityService;
    @Autowired
    private AuthenticationService authenticationService;
    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void setUp() {
        reset(authenticationService);
        cacheManager.getCache(CacheConfig.PERMISSION_CACHE).clear();
    }

    @Test
    void should_lookUpPermissionOnce_when_sameRoleMethodAndApiAreCheckedAgain() {
        //GIVEN
        when(authenticationService.checkPermission(any(), eq("GET"), eq("/api/jobs"))).thenReturn(true);

        //WHEN - the second call comes from another user (other token) with the same role
        boolean first = securityService.checkPermissionCached("USER_VIP_0", "GET", "/api/jobs", "Bearer token-1");
        boolean second = securityService.checkPermissionCached("USER_VIP_0", "GET", "/api/jobs", "Bearer token-2");

        //THEN
        assertThat(first).isTrue();
        assertThat(second).isTrue();
        verify(authenticationService, times(1)).checkPermission(any(), eq("GET"), eq("/api/jobs"));
    }

    @Test
    void should_cacheDenialToo_when_roleHasNoPermission() {
        //GIVEN
        when(authenticationService.checkPermission(any(), eq("DELETE"), eq("/api/jobs/{jobId}"))).thenReturn(false);

        //WHEN
        boolean first = securityService.checkPermissionCached("USER_VIP_0", "DELETE", "/api/jobs/{jobId}", "Bearer token-1");
        boolean second = securityService.checkPermissionCached("USER_VIP_0", "DELETE", "/api/jobs/{jobId}", "Bearer token-1");

        //THEN
        assertThat(first).isFalse();
        assertThat(second).isFalse();
        verify(authenticationService, times(1)).checkPermission(any(), eq("DELETE"), eq("/api/jobs/{jobId}"));
    }

    @Test
    void should_lookUpSeparately_when_roleMethodOrApiDiffers() {
        //GIVEN
        when(authenticationService.checkPermission(any(), any(), any())).thenReturn(true);

        //WHEN
        securityService.checkPermissionCached("USER_VIP_0", "GET", "/api/jobs", "Bearer token-1");
        securityService.checkPermissionCached("OTHER_ROLE", "GET", "/api/jobs", "Bearer token-1");
        securityService.checkPermissionCached("USER_VIP_0", "POST", "/api/jobs", "Bearer token-1");
        securityService.checkPermissionCached("USER_VIP_0", "GET", "/api/orders", "Bearer token-1");

        //THEN
        verify(authenticationService, times(4)).checkPermission(any(), any(), any());
    }

    @Test
    void should_notCacheFailure_when_lookupThrows() {
        //GIVEN
        when(authenticationService.checkPermission(any(), eq("GET"), eq("/api/jobs")))
                .thenThrow(new RuntimeException("DB unreachable"))
                .thenReturn(true);

        //WHEN
        assertThatThrownBy(() -> securityService.checkPermissionCached("USER_VIP_0", "GET", "/api/jobs", "Bearer token-1"))
                .isInstanceOf(RuntimeException.class);
        boolean retry = securityService.checkPermissionCached("USER_VIP_0", "GET", "/api/jobs", "Bearer token-1");

        //THEN
        assertThat(retry).isTrue();
        verify(authenticationService, times(2)).checkPermission(any(), eq("GET"), eq("/api/jobs"));
    }
}
