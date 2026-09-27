package com.master.service;

import com.master.dto.GoogleRoutesUsageResponse;
import com.master.entity.Configuration;
import com.master.entity.GoogleRoutesUsage;
import com.master.repository.ConfigurationRepository;
import com.master.repository.GoogleRoutesUsageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers {@link GoogleRoutesUsageService}: the atomic daily reservation (limit, rejection log, Thai
 * business day) and the usage report.
 */
@ExtendWith(MockitoExtension.class)
class GoogleRoutesUsageServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 20);

    @Mock
    private GoogleRoutesUsageRepository repository;
    @Mock
    private ConfigurationRepository configurationRepository;

    private GoogleRoutesUsageService service;

    /** 2026-09-20 12:00 in Asia/Bangkok */
    private Clock clockAt(String utcInstant) {
        return Clock.fixed(Instant.parse(utcInstant), com.common.config.ClockConfig.BUSINESS_ZONE);
    }

    @BeforeEach
    void setUp() {
        service = new GoogleRoutesUsageService(repository, configurationRepository, clockAt("2026-09-20T05:00:00Z"));
        ReflectionTestUtils.setField(service, "defaultDailyLimit", 100);
    }

    private GoogleRoutesUsage usage(LocalDate date, int calls, int rejected) {
        GoogleRoutesUsage row = new GoogleRoutesUsage();
        ReflectionTestUtils.setField(row, "usageDate", date);
        ReflectionTestUtils.setField(row, "callCount", calls);
        ReflectionTestUtils.setField(row, "rejectedCount", rejected);
        return row;
    }

    @Test
    void should_allow_call_and_not_record_rejection_when_under_daily_limit() {
        //GIVEN
        when(repository.incrementCallCountIfBelowLimit(TODAY, 100)).thenReturn(1);

        //WHEN
        boolean allowed = service.tryAcquire();

        //THEN
        assertThat(allowed).isTrue();
        verify(repository, never()).incrementRejectedCount(any());
    }

    @Test
    void should_reject_call_and_record_rejection_when_daily_limit_reached() {
        //GIVEN
        when(repository.incrementCallCountIfBelowLimit(TODAY, 100)).thenReturn(0);

        //WHEN
        boolean allowed = service.tryAcquire();

        //THEN
        assertThat(allowed).isFalse();
        verify(repository).incrementRejectedCount(TODAY);
    }

    @Test
    void should_reject_every_call_without_touching_the_counter_when_limit_is_zero() {
        //GIVEN
        ReflectionTestUtils.setField(service, "defaultDailyLimit", 0);

        //WHEN
        boolean allowed = service.tryAcquire();

        //THEN
        assertThat(allowed).isFalse();
        verify(repository, never()).incrementCallCountIfBelowLimit(any(), anyInt());
        verify(repository).incrementRejectedCount(TODAY);
    }

    @Test
    void should_count_against_the_thai_day_when_utc_date_is_still_the_previous_day() {
        //GIVEN 17:30 UTC on 09-20 is already 00:30 on 09-21 in Asia/Bangkok
        service = new GoogleRoutesUsageService(repository, configurationRepository, clockAt("2026-09-20T17:30:00Z"));
        ReflectionTestUtils.setField(service, "defaultDailyLimit", 100);
        when(repository.incrementCallCountIfBelowLimit(eq(LocalDate.of(2026, 9, 21)), eq(100))).thenReturn(1);

        //WHEN
        boolean allowed = service.tryAcquire();

        //THEN
        assertThat(allowed).isTrue();
        verify(repository).incrementCallCountIfBelowLimit(LocalDate.of(2026, 9, 21), 100);
    }

    @Test
    void should_report_today_count_remaining_and_history_when_rows_exist() {
        //GIVEN
        when(repository.findByUsageDateGreaterThanEqualOrderByUsageDateDesc(TODAY.minusDays(29)))
                .thenReturn(List.of(usage(TODAY, 12, 0), usage(TODAY.minusDays(1), 100, 7)));

        //WHEN
        GoogleRoutesUsageResponse result = service.getUsage(null);

        //THEN
        assertThat(result.getDailyLimit()).isEqualTo(100);
        assertThat(result.getTodayCallCount()).isEqualTo(12);
        assertThat(result.getTodayRemaining()).isEqualTo(88);
        assertThat(result.getHistory()).extracting(GoogleRoutesUsageResponse.DailyUsage::getDate)
                .containsExactly(TODAY, TODAY.minusDays(1));
        assertThat(result.getHistory().get(1).getCallCount()).isEqualTo(100);
        assertThat(result.getHistory().get(1).getRejectedCount()).isEqualTo(7);
    }

    @Test
    void should_report_zero_used_and_full_remaining_when_nothing_was_called_today() {
        //GIVEN
        when(repository.findByUsageDateGreaterThanEqualOrderByUsageDateDesc(any())).thenReturn(List.of());

        //WHEN
        GoogleRoutesUsageResponse result = service.getUsage(7);

        //THEN
        assertThat(result.getTodayCallCount()).isZero();
        assertThat(result.getTodayRemaining()).isEqualTo(100);
        assertThat(result.getHistory()).isEmpty();
        verify(repository).findByUsageDateGreaterThanEqualOrderByUsageDateDesc(TODAY.minusDays(6));
    }

    @Test
    void should_clamp_history_window_to_valid_range_when_days_is_out_of_bounds() {
        //GIVEN
        when(repository.findByUsageDateGreaterThanEqualOrderByUsageDateDesc(any())).thenReturn(List.of());

        //WHEN
        service.getUsage(0);
        service.getUsage(100_000);

        //THEN
        verify(repository).findByUsageDateGreaterThanEqualOrderByUsageDateDesc(TODAY);
        verify(repository).findByUsageDateGreaterThanEqualOrderByUsageDateDesc(TODAY.minusDays(365));
    }

    // ---------------------------------------------------------------------
    // Daily limit read from the configuration table (code GR_DAILY, value_1)
    // ---------------------------------------------------------------------

    private void givenLimitConfig(String value, String status) {
        when(configurationRepository.findByCode("GR_DAILY"))
                .thenReturn(Optional.of(Configuration.builder().code("GR_DAILY").value1(value).status(status).build()));
    }

    @Test
    void should_useLimitFromConfigurationTable_when_activeRowHasValidValue() {
        //GIVEN
        givenLimitConfig("5", "A");
        when(repository.incrementCallCountIfBelowLimit(TODAY, 5)).thenReturn(1);

        //WHEN
        boolean allowed = service.tryAcquire();

        //THEN
        assertThat(allowed).isTrue();
        assertThat(service.getDailyLimit()).isEqualTo(5);
        verify(repository).incrementCallCountIfBelowLimit(TODAY, 5);
    }

    @Test
    void should_acceptSurroundingWhitespace_when_configuredValueIsPadded() {
        //GIVEN
        givenLimitConfig(" 250 ", "A");

        //WHEN
        int limit = service.getDailyLimit();

        //THEN
        assertThat(limit).isEqualTo(250);
    }

    @Test
    void should_rejectEveryCall_when_configuredLimitIsZero() {
        //GIVEN
        givenLimitConfig("0", "A");

        //WHEN
        boolean allowed = service.tryAcquire();

        //THEN
        assertThat(allowed).isFalse();
        verify(repository, never()).incrementCallCountIfBelowLimit(any(), anyInt());
        verify(repository).incrementRejectedCount(TODAY);
    }

    @Test
    void should_useDefaultLimit_when_noConfigurationRowExists() {
        //GIVEN (configurationRepository returns Optional.empty by default)

        //WHEN
        int limit = service.getDailyLimit();

        //THEN
        assertThat(limit).isEqualTo(100);
    }

    @Test
    void should_useDefaultLimit_when_configurationRowIsNotActive() {
        //GIVEN
        givenLimitConfig("5", "I");

        //WHEN
        int limit = service.getDailyLimit();

        //THEN
        assertThat(limit).isEqualTo(100);
    }

    @Test
    void should_useDefaultLimit_when_configuredValueIsNotAValidNumber() {
        //GIVEN
        givenLimitConfig("many", "A");

        //WHEN
        int limit = service.getDailyLimit();

        //THEN
        assertThat(limit).isEqualTo(100);
    }

    @Test
    void should_useDefaultLimit_when_configuredValueIsNegativeOrMissing() {
        //GIVEN
        givenLimitConfig("-3", "A");
        assertThat(service.getDailyLimit()).isEqualTo(100);

        //WHEN a null value_1
        givenLimitConfig(null, "A");

        //THEN
        assertThat(service.getDailyLimit()).isEqualTo(100);
    }

    @Test
    void should_pickUpAChangedLimitImmediately_when_configurationIsUpdatedBetweenCalls() {
        //GIVEN
        givenLimitConfig("10", "A");
        assertThat(service.getDailyLimit()).isEqualTo(10);

        //WHEN the row is edited in the database
        givenLimitConfig("300", "A");

        //THEN no restart / cache refresh needed
        assertThat(service.getDailyLimit()).isEqualTo(300);
    }

    @Test
    void should_reportConfiguredLimitAndRemaining_when_usageIsRequested() {
        //GIVEN
        givenLimitConfig("20", "A");
        when(repository.findByUsageDateGreaterThanEqualOrderByUsageDateDesc(any()))
                .thenReturn(List.of(usage(TODAY, 12, 0)));

        //WHEN
        GoogleRoutesUsageResponse result = service.getUsage(null);

        //THEN
        assertThat(result.getDailyLimit()).isEqualTo(20);
        assertThat(result.getTodayRemaining()).isEqualTo(8);
    }
}
