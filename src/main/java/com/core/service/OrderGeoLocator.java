package com.core.service;

import com.common.util.GeoDistance;
import com.common.util.MapLinkCoords;
import com.core.entity.OrderAddress;
import com.core.repository.OrderAddressRepository;
import com.master.entity.Subdistrict;
import com.master.repository.SubdistrictRepository;
import com.master.service.GoogleGeocodingService;
import com.master.service.GoogleGeocodingService.GeocodeResult;
import com.master.service.MapLinkService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;

/**
 * เติมพิกัดบ้านลูกค้า ({@code lat} / {@code lng} / {@code geoSource}) ให้ที่อยู่ของ order ตอนบันทึก — ใช้จัดลำดับ
 * จุดแนะนำตอนสร้างงานให้แม่นกว่ากลางตำบล ลองตามลำดับ:
 * <ol>
 *   <li>หมุดในลิงก์ Google Maps (ลิงก์ย่อขยายด้วย {@link MapLinkService}) → {@value #SOURCE_MAP_LINK}</li>
 *   <li>Google Geocoding จาก "ที่อยู่ ต. อ. จ. รหัสไปรษณีย์" → {@value #SOURCE_GEOCODE} — รับเฉพาะผลที่ระบุจุดได้จริง
 *       (ไม่ใช่ {@code APPROXIMATE} และไม่ใช่แค่ชื่อพื้นที่) และอยู่ไม่เกิน {@value #MAX_KM_FROM_SUBDISTRICT} กม.
 *       จากกลางตำบลของ order (กัน Google ไปเจอที่อื่นที่ชื่อซ้ำ)</li>
 *   <li>ไม่ได้ทั้งคู่ → ล้างพิกัด (ตอนจัดลำดับใช้กลางตำบลแทน)</li>
 * </ol>
 * เรียก Google ไม่สำเร็จ / โควตาเต็ม ไม่ทำให้การบันทึก order ล้ม — แค่ไม่มีพิกัด
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderGeoLocator {

    public static final String SOURCE_MAP_LINK = "MAP_LINK";
    public static final String SOURCE_GEOCODE = "GEOCODE";
    /** ดูตัวอย่างเท่านั้น (ไม่บันทึก): ใช้กลางตำบล */
    public static final String SOURCE_SUBDISTRICT = "SUBDISTRICT";

    static final int MAX_KM_FROM_SUBDISTRICT = 15;
    private static final int COORD_SCALE = 6; // ~0.1 เมตร — เท่ากับ lat/lng ของตารางตำบล

    /** ผล geocode ที่เป็นแค่ชื่อพื้นที่ (ตำบล/อำเภอ/จังหวัด/รหัสไปรษณีย์) ไม่ใช่บ้าน — ไม่รับ */
    private static final Set<String> AREA_ONLY_TYPES = Set.of(
            "country", "administrative_area_level_1", "administrative_area_level_2", "administrative_area_level_3",
            "locality", "sublocality", "sublocality_level_1", "sublocality_level_2", "postal_code", "political");

    private final MapLinkService mapLinkService;
    private final GoogleGeocodingService geocodingService;
    private final SubdistrictRepository subdistrictRepository;
    private final OrderAddressRepository orderAddressRepository;

    /**
     * ผลการหาพิกัด — {@code source}: {@value #SOURCE_MAP_LINK} / {@value #SOURCE_GEOCODE} (ใช้ได้) หรือ
     * {@value #SOURCE_SUBDISTRICT} (ใช้กลางตำบลแทน, lat/lng อาจ null ถ้าตำบลไม่มีพิกัด) · {@code note}: ทำไม geocode
     * ไม่ได้ใช้ ({@code NO_ADDRESS} / {@code NOT_FOUND} / {@code APPROXIMATE} / {@code AREA_ONLY} / {@code TOO_FAR} /
     * {@code ERROR}) — null เมื่อได้พิกัดบ้าน
     */
    public record GeoOutcome(BigDecimal lat, BigDecimal lng, String source, String locationType, String note) {
        public boolean isHome() {
            return SOURCE_MAP_LINK.equals(source) || SOURCE_GEOCODE.equals(source);
        }
    }

    /** เติม/ล้างพิกัดของ {@code address} ตามข้อมูลปัจจุบันของมัน (ยังไม่ save) — {@code username} = เจ้าของ order */
    public void locate(OrderAddress address, String username) {
        GeoOutcome outcome = resolve(address, username);
        if (outcome.isHome()) {
            address.setLat(outcome.lat().toPlainString());
            address.setLng(outcome.lng().toPlainString());
            address.setGeoSource(outcome.source());
        } else {
            address.setLat(null);
            address.setLng(null);
            address.setGeoSource(null);
        }
    }

    /** หาพิกัดตามกฎเดียวกับตอนบันทึก แต่ไม่แก้อะไร — ใช้ทั้ง {@link #locate} และปุ่ม "ดูแผนที่" ในฟอร์ม order */
    public GeoOutcome resolve(OrderAddress address, String username) {
        Optional<MapLinkCoords.Point> pin = pinFromMapLink(address.getMapLink());
        if (pin.isPresent()) {
            return new GeoOutcome(scaled(pin.get().lat()), scaled(pin.get().lng()), SOURCE_MAP_LINK, null, null);
        }
        Subdistrict subdistrict = StringUtils.hasText(address.getSubdistrictCode())
                ? subdistrictRepository.findByCode(address.getSubdistrictCode().trim()).orElse(null)
                : null;
        if (subdistrict == null || !StringUtils.hasText(address.getAddressLine())) {
            return fallback(subdistrict, null, "NO_ADDRESS");
        }
        // ที่อยู่เดียวกันเคยหาพิกัดได้แล้ว (เช่น ลูกค้าเดิม) → ใช้ของเดิม ไม่เรียก Google ซ้ำ
        Optional<GeoOutcome> reused = reuseEarlierGeocode(address, username);
        if (reused.isPresent()) {
            return reused.get();
        }
        GeocodeResult result;
        try {
            result = geocodingService.geocode(addressText(address, subdistrict)).orElse(null);
        } catch (RuntimeException ex) {
            log.warn("Geocoding failed for subdistrict {}: {}", address.getSubdistrictCode(), ex.getMessage());
            return fallback(subdistrict, null, "ERROR");
        }
        if (result == null) {
            return fallback(subdistrict, null, "NOT_FOUND");
        }
        String rejected = rejection(result, subdistrict);
        if (rejected != null) {
            return fallback(subdistrict, result.locationType(), rejected);
        }
        return new GeoOutcome(scaled(result.lat()), scaled(result.lng()), SOURCE_GEOCODE, result.locationType(), null);
    }

    private Optional<GeoOutcome> reuseEarlierGeocode(OrderAddress address, String username) {
        if (!StringUtils.hasText(username)) {
            return Optional.empty();
        }
        return orderAddressRepository
                .findGeocodedSameAddress(username, address.getSubdistrictCode().trim(), address.getAddressLine())
                .filter(earlier -> earlier.getAddressId() == null || !earlier.getAddressId().equals(address.getAddressId()))
                .flatMap(earlier -> {
                    try {
                        return Optional.of(new GeoOutcome(new BigDecimal(earlier.getLat().trim()),
                                new BigDecimal(earlier.getLng().trim()), SOURCE_GEOCODE, null, null));
                    } catch (NumberFormatException ex) {
                        return Optional.empty();
                    }
                });
    }

    private static GeoOutcome fallback(Subdistrict subdistrict, String locationType, String note) {
        return new GeoOutcome(subdistrict == null ? null : subdistrict.getLat(),
                subdistrict == null ? null : subdistrict.getLng(), SOURCE_SUBDISTRICT, locationType, note);
    }

    private Optional<MapLinkCoords.Point> pinFromMapLink(String mapLink) {
        if (!StringUtils.hasText(mapLink)) {
            return Optional.empty();
        }
        Optional<MapLinkCoords.Point> direct = MapLinkCoords.parse(mapLink);
        if (direct.isPresent() || !mapLink.trim().toLowerCase().startsWith("https://")) {
            return direct;
        }
        try {
            return MapLinkCoords.parse(mapLinkService.resolve(mapLink.trim()));
        } catch (IllegalArgumentException ex) {
            return Optional.empty(); // ไม่ใช่ลิงก์ย่อ — ลิงก์เต็มที่ไม่มีพิกัด (เช่น /place/ชื่อ)
        } catch (RuntimeException ex) {
            log.warn("Could not expand map link for geo lookup: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    /** null = ใช้ได้ · ไม่งั้นเหตุผลที่ไม่รับ */
    private static String rejection(GeocodeResult result, Subdistrict subdistrict) {
        if (result.locationType() == null || "APPROXIMATE".equals(result.locationType())) {
            return "APPROXIMATE";
        }
        if (!result.types().isEmpty() && AREA_ONLY_TYPES.containsAll(result.types())) {
            return "AREA_ONLY";
        }
        BigDecimal km = GeoDistance.haversineKm(result.lat(), result.lng(), subdistrict.getLat(), subdistrict.getLng());
        // ตำบลไม่มีพิกัดกลาง = ตรวจไม่ได้ → ไม่เสี่ยงรับ
        if (km == null || km.compareTo(BigDecimal.valueOf(MAX_KM_FROM_SUBDISTRICT)) > 0) {
            return "TOO_FAR";
        }
        return null;
    }

    private static String addressText(OrderAddress address, Subdistrict subdistrict) {
        StringJoiner text = new StringJoiner(" ");
        text.add(address.getAddressLine().trim());
        text.add("ต." + subdistrict.getName());
        if (subdistrict.getDistrict() != null) {
            text.add("อ." + subdistrict.getDistrict().getName());
            if (subdistrict.getDistrict().getProvince() != null) {
                text.add("จ." + subdistrict.getDistrict().getProvince().getName());
            }
        }
        if (StringUtils.hasText(address.getZipCode())) {
            text.add(address.getZipCode().trim());
        }
        return text.toString();
    }

    private static BigDecimal scaled(BigDecimal value) {
        return value.setScale(COORD_SCALE, RoundingMode.HALF_UP);
    }
}
