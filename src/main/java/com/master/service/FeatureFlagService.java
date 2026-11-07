package com.master.service;

import com.master.entity.Configuration;
import com.master.repository.ConfigurationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Feature flags (เปิด/ปิด feature) stored as rows of {@code configuration} with group {@value #GROUP}.
 * <ul>
 *   <li>{@code value_1} = {@code Y} (on) / {@code N} (off)</li>
 *   <li>row missing, {@code status} not {@code A}, or {@code value_1} not Y/N → the caller's default</li>
 * </ul>
 * Read live on every call (no cache), so an UPDATE in the database takes effect without a restart:
 * {@code UPDATE configuration SET value_1 = 'N', updated_at = now() WHERE code = 'FF_REG';}
 */
@Service
@RequiredArgsConstructor
public class FeatureFlagService {

    public static final String GROUP = "feature_flag";

    /** Username + password log-in ({@code POST /auth/login}). Off = only LINE log-in (if on) works. */
    public static final String LOGIN = "FF_LOGIN";
    /** LINE log-in, sign-up and account link. Also needs {@code line.login.enabled=true} in application.yml. */
    public static final String LINE_LOGIN = "FF_LINE";
    /** New-account sign-up. Also needs {@code registration.enabled=true} in application.yml. */
    public static final String REGISTER = "FF_REG";

    private final ConfigurationRepository configurationRepository;

    @Transactional(readOnly = true)
    public boolean isEnabled(String code, boolean defaultValue) {
        Optional<String> value = configurationRepository.findByCode(code)
                .filter(c -> GROUP.equals(c.getGroupCode()) && "A".equals(c.getStatus()))
                .map(Configuration::getValue1)
                .map(v -> v.trim().toUpperCase());
        if (value.isEmpty()) {
            return defaultValue;
        }
        return switch (value.get()) {
            case "Y" -> true;
            case "N" -> false;
            default -> defaultValue;
        };
    }

    /** Default = on: a database without the row keeps today's behaviour. */
    public boolean isEnabled(String code) {
        return isEnabled(code, true);
    }
}
