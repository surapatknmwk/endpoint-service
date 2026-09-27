package com.master.service;

import com.master.dto.GoogleRoutesUsageResponse;
import com.master.entity.Configuration;
import com.master.entity.GoogleRoutesUsage;
import com.master.repository.ConfigurationRepository;
import com.master.repository.GoogleRoutesUsageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * คุมโควตารายวันของการยิง Google Routes API จริง และเก็บจำนวนที่ยิงไปแต่ละวันไว้ใน
 * {@code google_routes_usage} เป็น log ไว้ดูย้อนหลัง
 *
 * ค่าโควตาอ่านสดจากตาราง {@code configuration} (code = {@value #LIMIT_CONFIG_CODE}, {@code value_1}) ทุกครั้ง
 * จึงแก้ได้ตลอดโดยไม่ต้อง restart — ถ้าไม่มีแถว/ไม่ active/ค่าไม่ใช่จำนวนเต็ม >= 0 ใช้ค่าเริ่มต้นจาก
 * {@code google.maps.daily-request-limit} ใน application.yml (100)
 *
 * "วัน" นับตามเวลา Asia/Bangkok ({@link com.common.config.ClockConfig}) — โควตารีเซ็ตตอนเที่ยงคืนเวลาไทย
 * การจองโควตาเป็น atomic upsert ที่ฝั่ง database จึงไม่นับพลาดแม้มี request พร้อมกันหรือหลาย instance
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleRoutesUsageService {

    /** code ของแถวใน {@code configuration} ที่เก็บโควตารายวัน (value_1 = จำนวนครั้ง) */
    public static final String LIMIT_CONFIG_CODE = "GR_DAILY";

    private static final int DEFAULT_HISTORY_DAYS = 30;
    private static final int MAX_HISTORY_DAYS = 366;

    private final GoogleRoutesUsageRepository usageRepository;
    private final ConfigurationRepository configurationRepository;
    private final Clock clock;

    @Value("${google.maps.daily-request-limit:100}")
    private int defaultDailyLimit;

    /**
     * จองโควตา 1 ครั้งสำหรับการยิง Google จริง — คืน {@code true} ถ้ายังไม่เต็ม (นับเพิ่มแล้ว ผู้เรียกยิงได้เลย),
     * {@code false} ถ้าเต็ม (บันทึก rejected_count ไว้ ผู้เรียกต้องไม่ยิง)
     *
     * {@code REQUIRES_NEW} ตั้งใจ เพื่อให้ตัวนับ commit ทันทีและไม่ถูก rollback ตาม transaction ของผู้เรียก
     * (ยิง Google ไปแล้วก็เสียเงินแล้ว ต้องนับไว้แม้ผู้เรียกจะล้มเหลวทีหลัง)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean tryAcquire() {
        LocalDate today = LocalDate.now(clock);
        int dailyLimit = getDailyLimit();
        if (dailyLimit >= 1 && usageRepository.incrementCallCountIfBelowLimit(today, dailyLimit) == 1) {
            log.info("Google Routes API call reserved for {} (limit {}/day)", today, dailyLimit);
            return true;
        }
        usageRepository.incrementRejectedCount(today);
        log.warn("Google Routes API daily limit of {} reached for {} -- call rejected", dailyLimit, today);
        return false;
    }

    /** โควตารายวันตอนนี้: จาก {@code configuration} ถ้ามีแถว active ที่ค่าถูกต้อง ไม่งั้นค่าเริ่มต้นจาก application.yml */
    public int getDailyLimit() {
        Optional<Configuration> row = configurationRepository.findByCode(LIMIT_CONFIG_CODE)
                .filter(c -> "A".equals(c.getStatus()));
        if (row.isEmpty()) {
            return defaultDailyLimit;
        }
        String raw = row.get().getValue1();
        if (raw != null) {
            try {
                int configured = Integer.parseInt(raw.trim());
                if (configured >= 0) {
                    return configured;
                }
            } catch (NumberFormatException ignored) {
                // fall through to the default below
            }
        }
        log.warn("configuration {} has invalid value_1 '{}' (expected an integer >= 0) -- using default limit {}",
                LIMIT_CONFIG_CODE, raw, defaultDailyLimit);
        return defaultDailyLimit;
    }

    @Transactional(readOnly = true)
    public GoogleRoutesUsageResponse getUsage(Integer days) {
        int windowDays = days == null ? DEFAULT_HISTORY_DAYS : Math.min(Math.max(days, 1), MAX_HISTORY_DAYS);
        LocalDate today = LocalDate.now(clock);
        List<GoogleRoutesUsage> rows = usageRepository
                .findByUsageDateGreaterThanEqualOrderByUsageDateDesc(today.minusDays(windowDays - 1L));

        int todayCount = rows.stream()
                .filter(r -> r.getUsageDate().equals(today))
                .mapToInt(GoogleRoutesUsage::getCallCount)
                .findFirst()
                .orElse(0);

        int dailyLimit = getDailyLimit();
        return GoogleRoutesUsageResponse.builder()
                .dailyLimit(dailyLimit)
                .todayCallCount(todayCount)
                .todayRemaining(Math.max(dailyLimit - todayCount, 0))
                .history(rows.stream()
                        .map(r -> GoogleRoutesUsageResponse.DailyUsage.builder()
                                .date(r.getUsageDate())
                                .callCount(r.getCallCount())
                                .rejectedCount(r.getRejectedCount())
                                .build())
                        .toList())
                .build();
    }
}
