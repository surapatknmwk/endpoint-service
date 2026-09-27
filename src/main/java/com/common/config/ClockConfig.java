package com.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Single injectable {@link Clock} so "what day is it" logic (e.g. the Google Routes daily quota)
 * can be tested with a fixed clock. Uses Thai time so a "day" matches the business day, not UTC.
 */
@Configuration
public class ClockConfig {

    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Bangkok");

    @Bean
    public Clock clock() {
        return Clock.system(BUSINESS_ZONE);
    }
}
