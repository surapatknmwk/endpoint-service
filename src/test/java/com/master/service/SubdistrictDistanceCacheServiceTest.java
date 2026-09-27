package com.master.service;

import com.master.entity.Subdistrict;
import com.master.entity.SubdistrictDistance;
import com.master.repository.SubdistrictDistanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers {@link SubdistrictDistanceCacheService}: storing a Google Routes result with the
 * {@code googleFetched} flag, and reading back only the pairs that flag marks as already fetched.
 */
@ExtendWith(MockitoExtension.class)
class SubdistrictDistanceCacheServiceTest {

    private static final BigDecimal GOOGLE_KM = new BigDecimal("112.34");
    private static final Long GOOGLE_SECONDS = 6300L;

    @Mock
    private SubdistrictDistanceRepository repository;

    private SubdistrictDistanceCacheService service;

    private Subdistrict origin;
    private Subdistrict destination;

    @BeforeEach
    void setUp() {
        service = new SubdistrictDistanceCacheService(repository);
        origin = Subdistrict.builder().subdistrictId(1L).code("O").name("origin").build();
        destination = Subdistrict.builder().subdistrictId(2L).code("D").name("destination").build();
    }

    private void givenExistingRow(SubdistrictDistance row) {
        when(repository.findByOriginSubdistrictSubdistrictIdAndDestinationSubdistrictSubdistrictId(1L, 2L))
                .thenReturn(Optional.ofNullable(row));
    }

    private SubdistrictDistance haversineRow() {
        return SubdistrictDistance.builder()
                .originSubdistrict(origin)
                .destinationSubdistrict(destination)
                .distanceKm(new BigDecimal("80.00"))
                .build();
    }

    @Test
    void should_insert_flagged_google_row_when_pair_has_no_row() {
        //GIVEN
        givenExistingRow(null);

        //WHEN
        service.saveGoogleResult(origin, destination, GOOGLE_KM, GOOGLE_SECONDS);

        //THEN
        ArgumentCaptor<SubdistrictDistance> saved = ArgumentCaptor.forClass(SubdistrictDistance.class);
        verify(repository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().isGoogleFetched()).isTrue();
        assertThat(saved.getValue().getCalcMethod()).isEqualTo("google");
        assertThat(saved.getValue().getDistanceKm()).isEqualByComparingTo(GOOGLE_KM);
        assertThat(saved.getValue().getDurationSeconds()).isEqualTo(GOOGLE_SECONDS);
        assertThat(saved.getValue().getOriginSubdistrict()).isSameAs(origin);
        assertThat(saved.getValue().getDestinationSubdistrict()).isSameAs(destination);
    }

    @Test
    void should_overwrite_haversine_row_with_google_values_when_pair_not_yet_fetched() {
        //GIVEN
        SubdistrictDistance existing = haversineRow();
        givenExistingRow(existing);

        //WHEN
        service.saveGoogleResult(origin, destination, GOOGLE_KM, GOOGLE_SECONDS);

        //THEN
        assertThat(existing.isGoogleFetched()).isTrue();
        assertThat(existing.getCalcMethod()).isEqualTo("google");
        assertThat(existing.getDistanceKm()).isEqualByComparingTo(GOOGLE_KM);
        assertThat(existing.getDurationSeconds()).isEqualTo(GOOGLE_SECONDS);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void should_keep_existing_values_when_pair_already_fetched_from_google() {
        //GIVEN
        SubdistrictDistance existing = haversineRow();
        existing.setGoogleFetched(true);
        existing.setCalcMethod("google");
        existing.setDurationSeconds(1000L);
        givenExistingRow(existing);

        //WHEN
        service.saveGoogleResult(origin, destination, GOOGLE_KM, GOOGLE_SECONDS);

        //THEN
        assertThat(existing.getDistanceKm()).isEqualByComparingTo("80.00");
        assertThat(existing.getDurationSeconds()).isEqualTo(1000L);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void should_not_throw_when_concurrent_request_inserted_the_same_pair_first() {
        //GIVEN
        givenExistingRow(null);
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uq_subdistrict_distances_pair"));

        //WHEN + THEN
        assertThatCode(() -> service.saveGoogleResult(origin, destination, GOOGLE_KM, GOOGLE_SECONDS))
                .doesNotThrowAnyException();
    }

    @Test
    void should_return_row_when_pair_was_already_fetched_from_google() {
        //GIVEN
        SubdistrictDistance fetched = haversineRow();
        fetched.setGoogleFetched(true);
        givenExistingRow(fetched);

        //WHEN
        Optional<SubdistrictDistance> result = service.findGoogleFetched(1L, 2L);

        //THEN
        assertThat(result).containsSame(fetched);
    }

    @Test
    void should_return_empty_when_row_is_only_haversine() {
        //GIVEN
        givenExistingRow(haversineRow());

        //WHEN
        Optional<SubdistrictDistance> result = service.findGoogleFetched(1L, 2L);

        //THEN
        assertThat(result).isEmpty();
    }

    @Test
    void should_return_empty_when_pair_has_no_row() {
        //GIVEN
        givenExistingRow(null);

        //WHEN
        Optional<SubdistrictDistance> result = service.findGoogleFetched(1L, 2L);

        //THEN
        assertThat(result).isEmpty();
    }
}
