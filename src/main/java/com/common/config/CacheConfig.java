package com.common.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Single, application-wide cache manager for the consolidated service.
 *
 * <p>Registers core's permission check cache ({@link #PERMISSION_CACHE}, 1 hour TTL).</p>
 */
@Configuration
public class CacheConfig {

    public static final String PERMISSION_CACHE = "permissionCache";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCache permissionCache = new CaffeineCache(PERMISSION_CACHE,
                Caffeine.newBuilder()
                        .initialCapacity(100)
                        .maximumSize(1000)
                        .expireAfterWrite(1, TimeUnit.HOURS)
                        .recordStats()
                        .build());

        SimpleCacheManager cacheManager = new SimpleCacheManager();
        cacheManager.setCaches(List.of(permissionCache));
        return cacheManager;
    }
}
