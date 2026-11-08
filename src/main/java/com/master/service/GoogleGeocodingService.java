package com.master.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.master.exception.ExternalApiException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * แปลงข้อความที่อยู่เป็นพิกัด ผ่าน Google Geocoding API — ใช้ตอนบันทึก order ที่ไม่มีหมุดในลิงก์แผนที่
 * ({@code OrderGeoLocator}) · ใช้ API key ตัวเดียวกับ Routes ({@code google.maps.api-key}) และนับโควตารายวัน
 * ร่วมกับ Routes ({@link GoogleRoutesUsageService#tryAcquire}) เพื่อคุมค่าใช้จ่ายรวมจุดเดียว
 *
 * <p>คืน {@code Optional.empty()} เมื่อไม่มี key / โควตาเต็ม / Google หาไม่เจอ — throw {@link ExternalApiException}
 * เมื่อเรียก Google ไม่สำเร็จ (ผู้เรียกต้อง catch เอง การบันทึก order ต้องไม่ล้มเพราะเรื่องนี้)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleGeocodingService {

    private final GoogleRoutesUsageService usageService;

    @Value("${google.maps.api-key:}")
    private String apiKey;

    @Value("${google.maps.geocoding-base-url:https://maps.googleapis.com}")
    private String geocodingBaseUrl;

    private RestClient restClient;

    /** ผลจาก Google: พิกัด + ระดับความแม่น (ROOFTOP / RANGE_INTERPOLATED / GEOMETRIC_CENTER / APPROXIMATE) + ชนิดผล */
    public record GeocodeResult(BigDecimal lat, BigDecimal lng, String locationType, List<String> types) {
    }

    @PostConstruct
    private void init() {
        this.restClient = RestClient.builder().baseUrl(geocodingBaseUrl).build();
    }

    public Optional<GeocodeResult> geocode(String address) {
        if (!StringUtils.hasText(address)) {
            return Optional.empty();
        }
        if (!StringUtils.hasText(apiKey)) {
            log.debug("Google Maps API key not configured -- skipping geocoding");
            return Optional.empty();
        }
        if (!usageService.tryAcquire()) {
            log.warn("Google daily limit reached -- skipping geocoding");
            return Optional.empty();
        }

        GeocodeApiResponse response;
        try {
            response = restClient.get()
                    .uri(builder -> builder.path("/maps/api/geocode/json")
                            .queryParam("address", address)
                            .queryParam("components", "country:TH")
                            .queryParam("language", "th")
                            .queryParam("region", "th")
                            .queryParam("key", apiKey)
                            .build())
                    .retrieve()
                    .body(GeocodeApiResponse.class);
        } catch (RestClientException ex) {
            throw new ExternalApiException("Could not reach Google Geocoding API: " + ex.getMessage(), ex);
        }

        if (response == null || "ZERO_RESULTS".equals(response.status())) {
            return Optional.empty();
        }
        if (!"OK".equals(response.status())) {
            throw new ExternalApiException("Google Geocoding API returned " + response.status()
                    + (response.errorMessage() == null ? "" : ": " + response.errorMessage()));
        }
        if (response.results() == null || response.results().isEmpty()) {
            return Optional.empty();
        }
        GeocodeApiResult first = response.results().get(0);
        if (first.geometry() == null || first.geometry().location() == null) {
            return Optional.empty();
        }
        return Optional.of(new GeocodeResult(
                BigDecimal.valueOf(first.geometry().location().lat()),
                BigDecimal.valueOf(first.geometry().location().lng()),
                first.geometry().locationType(),
                first.types() == null ? List.of() : first.types()));
    }

    // Minimal response shape of the Geocoding API -- only the fields read above.
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GeocodeApiResponse(String status,
                                      @JsonProperty("error_message") String errorMessage,
                                      List<GeocodeApiResult> results) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GeocodeApiResult(List<String> types, Geometry geometry) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Geometry(LatLng location, @JsonProperty("location_type") String locationType) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record LatLng(double lat, double lng) {
    }
}
