package com.core.service.team;

import com.core.entity.DeliveryJob;
import com.core.entity.JobDriverRequest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** Expiry = earlier of requestedAt + 24h and 08:00 of the scheduled day, but at least requestedAt + 2h. */
class JobRequestPolicyTest {

    private final JobRequestPolicy policy = new JobRequestPolicy(24, 8, 2, 10);

    @ParameterizedTest
    @CsvSource(nullValues = "NULL", value = {
            // requested,         scheduled,           expected
            "2026-11-07T10:00, NULL,                2026-11-08T10:00",  // no date → 24 h
            "2026-11-07T10:00, 2026-11-08T00:00,    2026-11-08T08:00",  // 08:00 of the day comes first
            "2026-11-07T10:00, 2026-11-10T00:00,    2026-11-08T10:00",  // far date → 24 h
            "2026-11-07T10:00, 2026-11-07T00:00,    2026-11-07T12:00",  // same day, cutoff passed → at least 2 h
            "2026-11-07T07:00, 2026-11-07T00:00,    2026-11-07T09:00",  // cutoff 08:00 < 2 h minimum
            "2026-11-07T05:00, 2026-11-07T00:00,    2026-11-07T08:00"}) // boundary: cutoff after the 2 h minimum
    void should_pickEarlierOfTtlAndCutoffButKeepMinimum_when_computingExpiry(LocalDateTime requested,
                                                                            LocalDateTime scheduled,
                                                                            LocalDateTime expected) {
        //GIVEN
        JobDriverRequest request = JobDriverRequest.builder().requestedAt(requested).build();
        DeliveryJob job = DeliveryJob.builder().scheduledDate(scheduled).build();
        //WHEN / THEN
        assertThat(policy.expiresAt(request, job)).isEqualTo(expected);
        assertThat(policy.isExpired(request, job, expected.minusSeconds(1))).isFalse();
        assertThat(policy.isExpired(request, job, expected)).isTrue();
    }

    @Test
    void should_useTtl_when_jobIsUnknown() {
        //GIVEN — interface: the job row is gone
        JobDriverRequest request = JobDriverRequest.builder().requestedAt(LocalDateTime.of(2026, 11, 7, 10, 0)).build();
        //WHEN / THEN
        assertThat(policy.expiresAt(request, null)).isEqualTo(LocalDateTime.of(2026, 11, 8, 10, 0));
    }

    @Test
    void should_startLateWindowAtConfiguredHour_when_askedForADay() {
        assertThat(policy.lateFrom(LocalDate.of(2026, 11, 7))).isEqualTo(LocalDateTime.of(2026, 11, 7, 10, 0));
    }
}
