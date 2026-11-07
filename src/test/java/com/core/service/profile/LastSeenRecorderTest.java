package com.core.service.profile;

import com.authen.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Covers {@link LastSeenRecorder} (profile.md US-PROF-09).
 * <ul>
 *   <li>WHAT — writes users.last_seen_at at most once per user every 2 minutes, never failing the request.</li>
 *   <li>WHEN — first request, a second one inside / after the 2 minutes, another user, a database error.</li>
 *   <li>THEN — how many times the repository is asked to write, with which times.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class LastSeenRecorderTest {

    private static final ZoneId BANGKOK = ZoneId.of("Asia/Bangkok");
    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 2, 10, 0);

    @Mock
    private UserRepository userRepository;

    private MovableClock clock;
    private LastSeenRecorder recorder;

    @BeforeEach
    void setUp() {
        clock = new MovableClock(START.atZone(BANGKOK).toInstant());
        recorder = new LastSeenRecorder(userRepository, clock);
    }

    @Test
    void should_writeNowWithTwoMinuteGuard_when_firstRequestOfUser() {
        //GIVEN — nothing recorded yet
        //WHEN
        recorder.record("driver1");
        //THEN
        verify(userRepository).touchLastSeen("driver1", START, START.minusMinutes(2));
    }

    @Test
    void should_skipDatabase_when_sameUserWithinTwoMinutes() {
        //GIVEN
        recorder.record("driver1");
        clock.advance(Duration.ofSeconds(119));
        //WHEN
        recorder.record("driver1");
        //THEN
        verify(userRepository, times(1)).touchLastSeen(anyString(), any(), any());
    }

    @Test
    void should_writeAgain_when_twoMinutesHavePassed() {
        //GIVEN
        recorder.record("driver1");
        clock.advance(Duration.ofMinutes(2));
        //WHEN
        recorder.record("driver1");
        //THEN
        verify(userRepository).touchLastSeen("driver1", START.plusMinutes(2), START);
    }

    @Test
    void should_writeEachUser_when_twoUsersRequestTogether() {
        //GIVEN
        recorder.record("driver1");
        //WHEN
        recorder.record("admin");
        //THEN
        verify(userRepository).touchLastSeen(eq("admin"), any(), any());
    }

    @Test
    void should_notFailRequest_when_databaseWriteFails() {
        //GIVEN
        when(userRepository.touchLastSeen(anyString(), any(), any())).thenThrow(new IllegalStateException("db down"));
        //WHEN / THEN
        assertThatCode(() -> recorder.record("driver1")).doesNotThrowAnyException();
    }

    @Test
    void should_doNothing_when_usernameIsNull() {
        //GIVEN — a token without subject
        //WHEN
        recorder.record(null);
        //THEN
        verifyNoInteractions(userRepository);
    }

    /** Fixed clock that a test can move forward. */
    private static final class MovableClock extends Clock {
        private Instant now;

        private MovableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return BANGKOK;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
