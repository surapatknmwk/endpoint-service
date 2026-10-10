package com.authen.service;

import com.authen.constant.SecurityEventType;
import com.authen.entity.SecurityEvent;
import com.authen.repository.SecurityEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers {@link SecurityEventRecorder}: events are aggregated in memory and written as one
 * row per distinct event on flush, only when they reach the type's minimum occurrences.
 */
@ExtendWith(MockitoExtension.class)
class SecurityEventRecorderTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Bangkok");
    private static final Instant START = Instant.parse("2026-10-10T03:00:00Z");

    @Mock
    private SecurityEventRepository repository;
    @Mock
    private Clock clock;
    @Captor
    private ArgumentCaptor<List<SecurityEvent>> savedRows;

    private SecurityEventRecorder recorder;

    @BeforeEach
    void setUp() {
        lenient().when(clock.getZone()).thenReturn(ZONE);
        lenient().when(clock.instant()).thenReturn(START);
        recorder = new SecurityEventRecorder(repository, clock);
    }

    @Test
    void should_writeNothing_when_noEventWasRecorded() {
        //GIVEN nothing recorded

        //WHEN
        recorder.flush();

        //THEN
        verify(repository, never()).saveAll(any());
    }

    @Test
    void should_writeOneRowWithRequestDetails_when_singleEventRecorded() {
        //GIVEN
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/endpoint-authen-service/auth/login");
        request.addHeader("X-Forwarded-For", "203.0.113.9, 10.0.0.1");
        request.addHeader("User-Agent", "Mozilla/5.0");
        recorder.record(SecurityEventType.LOGIN_FAILED, request, "admin", "Wrong password");

        //WHEN
        recorder.flush();

        //THEN
        verify(repository).saveAll(savedRows.capture());
        assertThat(savedRows.getValue()).hasSize(1);
        SecurityEvent row = savedRows.getValue().get(0);
        assertThat(row.getEventType()).isEqualTo("LOGIN_FAILED");
        assertThat(row.getSeverity()).isEqualTo("MEDIUM");
        assertThat(row.getUsername()).isEqualTo("admin");
        assertThat(row.getIpAddress()).isEqualTo("203.0.113.9");
        assertThat(row.getHttpMethod()).isEqualTo("POST");
        assertThat(row.getRequestPath()).isEqualTo("/endpoint-authen-service/auth/login");
        assertThat(row.getUserAgent()).isEqualTo("Mozilla/5.0");
        assertThat(row.getDetail()).isEqualTo("Wrong password");
        assertThat(row.getOccurrenceCount()).isEqualTo(1);
        assertThat(row.getFirstSeenAt()).isEqualTo(LocalDateTime.of(2026, 10, 10, 10, 0, 0));
        assertThat(row.getLastSeenAt()).isEqualTo(row.getFirstSeenAt());
    }

    @Test
    void should_writeOneRowWithCountAndTimes_when_sameEventRepeatsManyTimes() {
        //GIVEN
        for (int second = 0; second < 5; second++) {
            when(clock.instant()).thenReturn(START.plusSeconds(second));
            recorder.record(SecurityEventType.RATE_LIMIT_EXCEEDED, "203.0.113.9", null, null, null, "curl/8", "flood");
        }

        //WHEN
        recorder.flush();

        //THEN
        verify(repository).saveAll(savedRows.capture());
        assertThat(savedRows.getValue()).hasSize(1);
        SecurityEvent row = savedRows.getValue().get(0);
        assertThat(row.getSeverity()).isEqualTo("HIGH");
        assertThat(row.getOccurrenceCount()).isEqualTo(5);
        assertThat(row.getFirstSeenAt()).isEqualTo(LocalDateTime.of(2026, 10, 10, 10, 0, 0));
        assertThat(row.getLastSeenAt()).isEqualTo(LocalDateTime.of(2026, 10, 10, 10, 0, 4));
        assertThat(row.getHttpMethod()).isNull();
        assertThat(row.getRequestPath()).isNull();
    }

    @Test
    void should_writeSeparateRows_when_eventsDifferByTypeIpUserOrPath() {
        //GIVEN
        recorder.record(SecurityEventType.LOGIN_FAILED, "203.0.113.1", "admin", "POST", "/login", null, null);
        recorder.record(SecurityEventType.LOGIN_FAILED, "203.0.113.2", "admin", "POST", "/login", null, null);
        recorder.record(SecurityEventType.LOGIN_FAILED, "203.0.113.1", "other", "POST", "/login", null, null);
        recorder.record(SecurityEventType.PERMISSION_DENIED, "203.0.113.1", "admin", "POST", "/login", null, null);
        recorder.record(SecurityEventType.PERMISSION_DENIED, "203.0.113.1", "admin", "POST", "/other", null, null);

        //WHEN
        recorder.flush();

        //THEN
        verify(repository).saveAll(savedRows.capture());
        assertThat(savedRows.getValue()).hasSize(5);
    }

    @Test
    void should_writeNothing_when_duplicateRequestIsBelowMinimumOccurrences() {
        //GIVEN an ordinary double click (and one more): 2 rejections, minimum is 3
        recorder.record(SecurityEventType.DUPLICATE_REQUEST, "203.0.113.1", "admin", "POST", "/orders", null, null);
        recorder.record(SecurityEventType.DUPLICATE_REQUEST, "203.0.113.1", "admin", "POST", "/orders", null, null);

        //WHEN
        recorder.flush();

        //THEN
        verify(repository, never()).saveAll(any());
    }

    @Test
    void should_writeRow_when_duplicateRequestReachesMinimumOccurrences() {
        //GIVEN
        for (int i = 0; i < 3; i++) {
            recorder.record(SecurityEventType.DUPLICATE_REQUEST, "203.0.113.1", "admin", "POST", "/orders", null, null);
        }

        //WHEN
        recorder.flush();

        //THEN
        verify(repository).saveAll(savedRows.capture());
        assertThat(savedRows.getValue()).singleElement()
                .satisfies(row -> {
                    assertThat(row.getSeverity()).isEqualTo("LOW");
                    assertThat(row.getOccurrenceCount()).isEqualTo(3);
                });
    }

    @Test
    void should_startCountingAgain_when_eventRepeatsAfterFlush() {
        //GIVEN
        recorder.record(SecurityEventType.LOGIN_FAILED, "203.0.113.1", "admin", "POST", "/login", null, null);
        recorder.record(SecurityEventType.LOGIN_FAILED, "203.0.113.1", "admin", "POST", "/login", null, null);
        recorder.flush();
        recorder.record(SecurityEventType.LOGIN_FAILED, "203.0.113.1", "admin", "POST", "/login", null, null);

        //WHEN
        recorder.flush();

        //THEN
        verify(repository, times(2)).saveAll(savedRows.capture());
        assertThat(savedRows.getAllValues().get(0).get(0).getOccurrenceCount()).isEqualTo(2);
        assertThat(savedRows.getAllValues().get(1).get(0).getOccurrenceCount()).isEqualTo(1);
    }

    @Test
    void should_dropNewEventsButKeepCountingKnownOnes_when_pendingLimitIsReached() {
        //GIVEN
        for (int i = 0; i < SecurityEventRecorder.MAX_PENDING; i++) {
            recorder.record(SecurityEventType.LOGIN_FAILED, "203.0.113.1", "user" + i, "POST", "/login", null, null);
        }
        recorder.record(SecurityEventType.LOGIN_FAILED, "203.0.113.1", "one-too-many", "POST", "/login", null, null);
        recorder.record(SecurityEventType.LOGIN_FAILED, "203.0.113.1", "user0", "POST", "/login", null, null);

        //WHEN
        recorder.flush();

        //THEN
        verify(repository).saveAll(savedRows.capture());
        List<SecurityEvent> rows = savedRows.getValue();
        assertThat(rows).hasSize(SecurityEventRecorder.MAX_PENDING);
        assertThat(rows).noneMatch(row -> "one-too-many".equals(row.getUsername()));
        assertThat(rows).filteredOn(row -> "user0".equals(row.getUsername()))
                .singleElement().satisfies(row -> assertThat(row.getOccurrenceCount()).isEqualTo(2));
    }

    @Test
    void should_truncateLongValues_when_fieldsExceedColumnLength() {
        //GIVEN
        recorder.record(SecurityEventType.LOGIN_FAILED, "203.0.113.1", "u".repeat(80), "POST",
                "/" + "p".repeat(600), "a".repeat(700), null);

        //WHEN
        recorder.flush();

        //THEN
        verify(repository).saveAll(savedRows.capture());
        SecurityEvent row = savedRows.getValue().get(0);
        assertThat(row.getUsername()).hasSize(50);
        assertThat(row.getRequestPath()).hasSize(500);
        assertThat(row.getUserAgent()).hasSize(500);
    }

    @Test
    void should_notThrowAndKeepWorking_when_databaseWriteFails() {
        //GIVEN
        doThrow(new IllegalStateException("relation \"security_events\" does not exist"))
                .doReturn(List.of())
                .when(repository).saveAll(any());
        recorder.record(SecurityEventType.LOGIN_FAILED, "203.0.113.1", "admin", "POST", "/login", null, null);

        //WHEN
        assertThatCode(() -> recorder.flush()).doesNotThrowAnyException();
        recorder.record(SecurityEventType.LOGIN_FAILED, "203.0.113.1", "admin", "POST", "/login", null, null);
        recorder.flush();

        //THEN
        verify(repository, times(2)).saveAll(savedRows.capture());
        assertThat(savedRows.getAllValues().get(1)).hasSize(1);
    }

    @Test
    void should_useRemoteAddress_when_requestHasNoForwardedHeader() {
        //GIVEN
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/x");
        request.setRemoteAddr("198.51.100.7");

        //WHEN
        String ip = SecurityEventRecorder.clientIp(request);

        //THEN
        assertThat(ip).isEqualTo("198.51.100.7");
    }
}
