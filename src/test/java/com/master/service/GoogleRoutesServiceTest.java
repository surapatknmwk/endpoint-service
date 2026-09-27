package com.master.service;

import com.master.dto.GoogleRouteDistanceResponse;
import com.master.entity.District;
import com.master.entity.Province;
import com.master.entity.Subdistrict;
import com.master.entity.SubdistrictDistance;
import com.master.exception.ExternalApiException;
import com.master.exception.GoogleRoutesQuotaExceededException;
import com.master.repository.SubdistrictRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Covers the store-and-reuse behaviour of {@link GoogleRoutesService#findRoute}: a pair already
 * flagged {@code googleFetched} must never reach Google again, and every real Google call must be
 * stored in both directions. Google itself is replaced by {@link MockRestServiceServer}.
 */
@ExtendWith(MockitoExtension.class)
class GoogleRoutesServiceTest {

    private static final String BASE_URL = "https://routes.test";
    private static final String GOOGLE_BODY = "{\"routes\":[{\"distanceMeters\":112340,\"duration\":\"6300s\"}]}";

    @Mock
    private SubdistrictRepository subdistrictRepository;
    @Mock
    private SubdistrictDistanceCacheService cacheService;
    @Mock
    private GoogleRoutesUsageService usageService;

    private GoogleRoutesService service;
    private MockRestServiceServer google;

    private Subdistrict origin;
    private Subdistrict destination;

    @BeforeEach
    void setUp() {
        service = new GoogleRoutesService(subdistrictRepository, cacheService, usageService);
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        google = MockRestServiceServer.bindTo(builder).build();
        ReflectionTestUtils.setField(service, "restClient", builder.build());
        ReflectionTestUtils.setField(service, "apiKey", "test-key");

        Province province = Province.builder().provinceId(1L).code("P").name("Province").build();
        District district = District.builder().districtId(1L).code("DI").name("District").province(province).build();
        origin = subdistrict(1L, "O", district);
        destination = subdistrict(2L, "D", district);
        lenient().when(usageService.tryAcquire()).thenReturn(true);
        lenient().when(subdistrictRepository.findByCode("O")).thenReturn(Optional.of(origin));
        lenient().when(subdistrictRepository.findByCode("D")).thenReturn(Optional.of(destination));
    }

    private Subdistrict subdistrict(long id, String code, District district) {
        return Subdistrict.builder()
                .subdistrictId(id)
                .code(code)
                .name("Subdistrict-" + code)
                .district(district)
                .lat(new BigDecimal("16.000000"))
                .lng(new BigDecimal("103.000000"))
                .build();
    }

    private void givenPairAlreadyFetchedFromGoogle() {
        SubdistrictDistance fetched = SubdistrictDistance.builder()
                .originSubdistrict(origin)
                .destinationSubdistrict(destination)
                .distanceKm(new BigDecimal("98.76"))
                .durationSeconds(5400L)
                .googleFetched(true)
                .build();
        when(cacheService.findGoogleFetched(1L, 2L)).thenReturn(Optional.of(fetched));
    }

    private void givenGoogleAnswers() {
        google.expect(requestTo(BASE_URL + "/directions/v2:computeRoutes"))
                .andRespond(withSuccess(GOOGLE_BODY, MediaType.APPLICATION_JSON));
    }

    @Test
    void should_return_stored_result_without_calling_google_when_pair_already_fetched() {
        //GIVEN
        givenPairAlreadyFetchedFromGoogle();

        //WHEN
        GoogleRouteDistanceResponse result = service.findRoute("O", "D");

        //THEN
        assertThat(result.getFromCache()).isTrue();
        assertThat(result.getDistanceKm()).isEqualByComparingTo("98.76");
        assertThat(result.getDistanceMeters()).isEqualTo(98760);
        assertThat(result.getDurationSeconds()).isEqualTo(5400L);
        google.verify(); // no expectation was registered, so any call to Google would have failed the test
        verify(cacheService, never()).saveGoogleResult(any(), any(), any(), anyLong());
        verify(usageService, never()).tryAcquire();
    }

    @Test
    void should_return_stored_result_without_api_key_when_pair_already_fetched() {
        //GIVEN
        ReflectionTestUtils.setField(service, "apiKey", "");
        givenPairAlreadyFetchedFromGoogle();

        //WHEN
        GoogleRouteDistanceResponse result = service.findRoute("O", "D");

        //THEN
        assertThat(result.getFromCache()).isTrue();
        assertThat(result.getDistanceKm()).isEqualByComparingTo("98.76");
    }

    @Test
    void should_call_google_and_store_both_directions_when_pair_not_fetched() {
        //GIVEN
        when(cacheService.findGoogleFetched(1L, 2L)).thenReturn(Optional.empty());
        givenGoogleAnswers();

        //WHEN
        GoogleRouteDistanceResponse result = service.findRoute("O", "D");

        //THEN
        assertThat(result.getFromCache()).isFalse();
        assertThat(result.getDistanceKm()).isEqualByComparingTo("112.34");
        assertThat(result.getDistanceMeters()).isEqualTo(112340);
        assertThat(result.getDurationSeconds()).isEqualTo(6300L);
        google.verify();
        verify(usageService).tryAcquire();
        verify(cacheService).saveGoogleResult(eq(origin), eq(destination), eq(new BigDecimal("112.34")), eq(6300L));
        verify(cacheService).saveGoogleResult(eq(destination), eq(origin), eq(new BigDecimal("112.34")), eq(6300L));
    }

    @Test
    void should_still_return_google_result_when_storing_it_fails() {
        //GIVEN
        when(cacheService.findGoogleFetched(1L, 2L)).thenReturn(Optional.empty());
        givenGoogleAnswers();
        doThrow(new IllegalStateException("db down")).when(cacheService).saveGoogleResult(any(), any(), any(), anyLong());

        //WHEN
        GoogleRouteDistanceResponse result = service.findRoute("O", "D");

        //THEN
        assertThat(result.getDistanceKm()).isEqualByComparingTo("112.34");
        assertThat(result.getFromCache()).isFalse();
    }

    @Test
    void should_fail_with_external_api_error_when_pair_not_fetched_and_no_api_key() {
        //GIVEN
        ReflectionTestUtils.setField(service, "apiKey", "");
        when(cacheService.findGoogleFetched(1L, 2L)).thenReturn(Optional.empty());

        //WHEN + THEN
        assertThatThrownBy(() -> service.findRoute("O", "D"))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("API key");
        verify(cacheService, never()).saveGoogleResult(any(), any(), any(), anyLong());
        verify(usageService, never()).tryAcquire();
    }

    @Test
    void should_reject_with_quota_error_and_not_call_google_when_daily_limit_reached() {
        //GIVEN
        when(cacheService.findGoogleFetched(1L, 2L)).thenReturn(Optional.empty());
        when(usageService.tryAcquire()).thenReturn(false);
        when(usageService.getDailyLimit()).thenReturn(100);

        //WHEN + THEN
        assertThatThrownBy(() -> service.findRoute("O", "D"))
                .isInstanceOf(GoogleRoutesQuotaExceededException.class)
                .hasMessageContaining("100");
        google.verify(); // no expectation was registered, so any call to Google would have failed the test
        verify(cacheService, never()).saveGoogleResult(any(), any(), any(), anyLong());
    }

    @Test
    void should_not_use_quota_when_subdistrict_has_no_lat_lng() {
        //GIVEN
        origin.setLat(null);
        when(cacheService.findGoogleFetched(1L, 2L)).thenReturn(Optional.empty());

        //WHEN + THEN
        assertThatThrownBy(() -> service.findRoute("O", "D"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lat/lng");
        verify(usageService, never()).tryAcquire();
    }

    @Test
    void should_reject_request_when_origin_code_is_blank() {
        //WHEN + THEN
        assertThatThrownBy(() -> service.findRoute(" ", "D"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(cacheService, never()).findGoogleFetched(anyLong(), anyLong());
    }
}
