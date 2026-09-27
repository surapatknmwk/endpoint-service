package com.master.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.master.dto.GoogleRouteDistanceResponse;
import com.master.dto.LocationPointDto;
import com.master.entity.Subdistrict;
import com.master.entity.SubdistrictDistance;
import com.master.exception.ExternalApiException;
import com.master.exception.GoogleRoutesQuotaExceededException;
import com.master.exception.ResourceNotFoundException;
import com.master.repository.SubdistrictRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

/**
 * ดึงระยะทาง/เวลาเดินทาง "จริงตามถนน" จาก Google Routes API (computeRoutes) โดยตรง —
 * แหล่งเดียวที่ endpoint /subdistrict-distances/google-distance ใช้ (ตัด adjacency-graph
 * routing และ Haversine on-demand calculation ที่เคยมีออกไปแล้ว เพราะ Google ให้ค่าจริง
 * ตามถนนมาแทนได้เลย)
 *
 * ใช้ routingPreference=TRAFFIC_UNAWARE โดยตั้งใจ (ไม่ขอ traffic-aware duration) เพื่อให้
 * request นี้ตกอยู่ใน Essentials tier ของ Google (ถูกกว่า Pro tier ที่ต้องใช้เมื่อขอฟีเจอร์
 * ที่เกี่ยวกับสภาพจราจร)
 *
 * ทุกครั้งที่เรียก Google สำเร็จ ผลลัพธ์จะถูกบันทึกลง {@code subdistrict_distances} ทั้งสองทิศทาง พร้อม
 * flag {@code googleFetched = true} ({@link SubdistrictDistanceCacheService}) — ครั้งถัดไปที่ถามคู่ตำบล
 * เดียวกัน (ทิศไหนก็ได้ จาก endpoint ไหนก็ได้) จะอ่านจากตารางแทน ไม่เรียก Google ซ้ำ (และไม่ต้องมี API key)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleRoutesService {

    private static final String FIELD_MASK = "routes.distanceMeters,routes.duration";

    private final SubdistrictRepository subdistrictRepository;
    private final SubdistrictDistanceCacheService subdistrictDistanceCacheService;
    private final GoogleRoutesUsageService usageService;

    @Value("${google.maps.api-key:}")
    private String apiKey;

    @Value("${google.maps.routes-base-url:https://routes.googleapis.com}")
    private String routesBaseUrl;

    private RestClient restClient;

    @PostConstruct
    private void init() {
        this.restClient = RestClient.builder().baseUrl(routesBaseUrl).build();
    }

    /**
     * {@code REQUIRES_NEW} ตั้งใจ — ถ้าเรียกจากภายใน transaction อื่นที่กำลังเปิดอยู่ (เช่น
     * {@code JobService.getCandidateOrders}) แล้ว method นี้ throw (เช่น subdistrict ยังไม่มี
     * lat/lng) จะ mark เฉพาะ transaction ใหม่ของตัวเองเป็น rollback-only ไม่ใช่ transaction ของ
     * ผู้เรียก — ผู้เรียก catch แล้วทำงานต่อได้โดยไม่โดน {@code UnexpectedRollbackException}
     * ตอน commit ทีหลัง
     */
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public GoogleRouteDistanceResponse findRoute(String originCode, String destinationCode) {
        if (!StringUtils.hasText(originCode) || !StringUtils.hasText(destinationCode)) {
            throw new IllegalArgumentException("originCode and destinationCode are required");
        }

        String originCodeTrimmed = originCode.trim();
        String destinationCodeTrimmed = destinationCode.trim();

        Subdistrict origin = subdistrictRepository.findByCode(originCodeTrimmed)
                .orElseThrow(() -> new ResourceNotFoundException("Subdistrict", "code", originCodeTrimmed));
        Subdistrict destination = subdistrictRepository.findByCode(destinationCodeTrimmed)
                .orElseThrow(() -> new ResourceNotFoundException("Subdistrict", "code", destinationCodeTrimmed));

        Optional<SubdistrictDistance> cached = subdistrictDistanceCacheService
                .findGoogleFetched(origin.getSubdistrictId(), destination.getSubdistrictId());
        if (cached.isPresent()) {
            return toCachedResponse(origin, destination, cached.get());
        }

        if (!StringUtils.hasText(apiKey)) {
            throw new ExternalApiException(
                    "Google Maps API key is not configured -- set the GOOGLE_MAPS_API_KEY environment variable "
                            + "(google.maps.api-key in application.yml) before calling this endpoint");
        }

        if (origin.getLat() == null || origin.getLng() == null) {
            throw new IllegalArgumentException(
                    "Subdistrict '" + originCodeTrimmed + "' has no lat/lng yet -- run sync-subdistricts first");
        }
        if (destination.getLat() == null || destination.getLng() == null) {
            throw new IllegalArgumentException(
                    "Subdistrict '" + destinationCodeTrimmed + "' has no lat/lng yet -- run sync-subdistricts first");
        }

        // จองโควตารายวันเป็นขั้นสุดท้ายก่อนยิงจริง — cache hit และ request ที่ไม่ผ่าน validation ข้างบนไม่กินโควตา
        if (!usageService.tryAcquire()) {
            throw new GoogleRoutesQuotaExceededException(
                    "Daily Google Routes API limit of " + usageService.getDailyLimit()
                            + " requests reached -- try again tomorrow (limit resets at midnight Asia/Bangkok), "
                            + "or use a pair whose distance is already stored");
        }

        ComputeRoutesRequest requestBody = new ComputeRoutesRequest(
                new Waypoint(new Location(new LatLng(origin.getLat().doubleValue(), origin.getLng().doubleValue()))),
                new Waypoint(new Location(new LatLng(destination.getLat().doubleValue(), destination.getLng().doubleValue()))),
                "DRIVE",
                "TRAFFIC_UNAWARE");

        GoogleRoutesApiResponse apiResponse;
        try {
            apiResponse = restClient.post()
                    .uri("/directions/v2:computeRoutes")
                    .header("Content-Type", "application/json")
                    .header("X-Goog-Api-Key", apiKey)
                    .header("X-Goog-FieldMask", FIELD_MASK)
                    .body(requestBody)
                    .retrieve()
                    .body(GoogleRoutesApiResponse.class);
        } catch (RestClientResponseException ex) {
            log.error("Google Routes API returned {}: {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new ExternalApiException(
                    "Google Routes API returned " + ex.getStatusCode() + ": " + ex.getResponseBodyAsString(), ex);
        } catch (RestClientException ex) {
            throw new ExternalApiException("Could not reach Google Routes API: " + ex.getMessage(), ex);
        }

        if (apiResponse == null || apiResponse.routes() == null || apiResponse.routes().isEmpty()) {
            throw new ExternalApiException(
                    "Google Routes API returned no route between '" + originCodeTrimmed + "' and '"
                            + destinationCodeTrimmed + "'");
        }

        GoogleRoute route = apiResponse.routes().get(0);
        Integer distanceMeters = route.distanceMeters();
        Long durationSeconds = parseDurationSeconds(route.duration());

        BigDecimal distanceKm = distanceMeters == null
                ? null
                : BigDecimal.valueOf(distanceMeters).divide(BigDecimal.valueOf(1000), 2, RoundingMode.HALF_UP);
        if (distanceKm != null) {
            saveGoogleResult(origin, destination, distanceKm, durationSeconds);
        }

        return GoogleRouteDistanceResponse.builder()
                .origin(toPointDto(origin))
                .destination(toPointDto(destination))
                .distanceMeters(distanceMeters)
                .distanceKm(distanceKm)
                .durationSeconds(durationSeconds)
                .fromCache(false)
                .build();
    }

    /**
     * บันทึกทั้งสองทิศทาง (แยกคนละ transaction) — ถ้าบันทึกไม่สำเร็จ (race, DB error) แค่ log แล้วไปต่อ
     * เพราะผู้เรียกได้ค่าจาก Google ไปแล้ว ครั้งหน้าจะเรียก Google ใหม่แล้วบันทึกอีกรอบ
     */
    private void saveGoogleResult(Subdistrict origin, Subdistrict destination, BigDecimal distanceKm, Long durationSeconds) {
        try {
            subdistrictDistanceCacheService.saveGoogleResult(origin, destination, distanceKm, durationSeconds);
        } catch (RuntimeException ex) {
            log.warn("Could not store Google distance {} -> {}: {}", origin.getCode(), destination.getCode(), ex.getMessage());
        }
        try {
            subdistrictDistanceCacheService.saveGoogleResult(destination, origin, distanceKm, durationSeconds);
        } catch (RuntimeException ex) {
            log.warn("Could not store Google distance {} -> {}: {}", destination.getCode(), origin.getCode(), ex.getMessage());
        }
    }

    /** distanceMeters คำนวณกลับจาก distanceKm ที่เก็บไว้ (ละเอียดระดับ 10 เมตร) */
    private GoogleRouteDistanceResponse toCachedResponse(Subdistrict origin, Subdistrict destination,
                                                          SubdistrictDistance cached) {
        return GoogleRouteDistanceResponse.builder()
                .origin(toPointDto(origin))
                .destination(toPointDto(destination))
                .distanceMeters(cached.getDistanceKm().movePointRight(3).intValue())
                .distanceKm(cached.getDistanceKm())
                .durationSeconds(cached.getDurationSeconds())
                .fromCache(true)
                .build();
    }

    /** Google returns duration as a string like "6300s" -- parse the seconds out. */
    private static Long parseDurationSeconds(String duration) {
        if (duration == null || !duration.endsWith("s")) {
            return null;
        }
        try {
            return Long.parseLong(duration.substring(0, duration.length() - 1));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private LocationPointDto toPointDto(Subdistrict subdistrict) {
        var district = subdistrict.getDistrict();
        var province = district == null ? null : district.getProvince();
        return LocationPointDto.builder()
                .provinceId(province == null ? null : province.getProvinceId())
                .provinceName(province == null ? null : province.getName())
                .districtId(district == null ? null : district.getDistrictId())
                .districtName(district == null ? null : district.getName())
                .subdistrictId(subdistrict.getSubdistrictId())
                .subdistrictName(subdistrict.getName())
                .build();
    }

    // ---------------------------------------------------------------------
    // Minimal request/response shapes for Google's computeRoutes -- only the
    // fields we actually send/read, not a full mirror of Google's schema.
    // ---------------------------------------------------------------------

    private record LatLng(double latitude, double longitude) {
    }

    private record Location(LatLng latLng) {
    }

    private record Waypoint(Location location) {
    }

    private record ComputeRoutesRequest(Waypoint origin, Waypoint destination, String travelMode, String routingPreference) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GoogleRoute(Integer distanceMeters, String duration) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GoogleRoutesApiResponse(List<GoogleRoute> routes) {
    }
}
