package com.core.service;

import com.core.entity.OrderAddress;
import com.core.repository.OrderAddressRepository;
import com.master.entity.District;
import com.master.entity.Province;
import com.master.entity.Subdistrict;
import com.master.exception.ExternalApiException;
import com.master.repository.SubdistrictRepository;
import com.master.service.GoogleGeocodingService;
import com.master.service.GoogleGeocodingService.GeocodeResult;
import com.master.service.MapLinkService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderGeoLocatorTest {

    @Mock
    private MapLinkService mapLinkService;
    @Mock
    private GoogleGeocodingService geocodingService;
    @Mock
    private SubdistrictRepository subdistrictRepository;
    @Mock
    private OrderAddressRepository orderAddressRepository;

    private OrderGeoLocator locator;

    // กลางตำบล S1 อยู่ที่ 18.0, 99.0
    private final Subdistrict subdistrict = Subdistrict.builder()
            .subdistrictId(1L).code("S1").name("สุเทพ")
            .lat(new BigDecimal("18.000000")).lng(new BigDecimal("99.000000"))
            .district(District.builder().districtId(1L).code("D1").name("เมืองเชียงใหม่")
                    .province(Province.builder().provinceId(1L).code("P1").name("เชียงใหม่").build()).build())
            .build();

    @BeforeEach
    void setUp() {
        locator = new OrderGeoLocator(mapLinkService, geocodingService, subdistrictRepository, orderAddressRepository);
    }

    private OrderAddress address(String mapLink) {
        return OrderAddress.builder().addressLine("12 ม.3").subdistrictCode("S1").zipCode("50200")
                .mapLink(mapLink).lat("1").lng("1").geoSource("OLD").build();
    }

    private GeocodeResult result(String lat, String lng, String locationType, String... types) {
        return new GeocodeResult(new BigDecimal(lat), new BigDecimal(lng), locationType, List.of(types));
    }

    @Test
    void should_usePinFromMapLink_when_linkHasCoordinates() {
        //GIVEN
        OrderAddress address = address("https://www.google.com/maps/place/x/@18.1,99.1,17z/data=!3d18.0123!4d99.0456");

        //WHEN
        locator.locate(address, "owner");

        //THEN - the place pin (!3d!4d) wins over the map centre (@), and Google geocoding is not called
        assertThat(address.getLat()).isEqualTo("18.012300");
        assertThat(address.getLng()).isEqualTo("99.045600");
        assertThat(address.getGeoSource()).isEqualTo(OrderGeoLocator.SOURCE_MAP_LINK);
        verify(geocodingService, never()).geocode(anyString());
    }

    @Test
    void should_expandShortLink_when_linkIsShort() {
        //GIVEN
        OrderAddress address = address("https://maps.app.goo.gl/abc");
        when(mapLinkService.resolve("https://maps.app.goo.gl/abc"))
                .thenReturn("https://www.google.com/maps/search/?api=1&query=18.02,99.03");

        //WHEN
        locator.locate(address, "owner");

        //THEN
        assertThat(address.getGeoSource()).isEqualTo(OrderGeoLocator.SOURCE_MAP_LINK);
        assertThat(address.getLat()).isEqualTo("18.020000");
    }

    @Test
    void should_geocodeAddressText_when_noMapLink() {
        //GIVEN - ~2 km from the subdistrict centre, a street address result
        OrderAddress address = address(null);
        when(subdistrictRepository.findByCode("S1")).thenReturn(Optional.of(subdistrict));
        when(geocodingService.geocode("12 ม.3 ต.สุเทพ อ.เมืองเชียงใหม่ จ.เชียงใหม่ 50200"))
                .thenReturn(Optional.of(result("18.018", "99.0", "ROOFTOP", "street_address")));

        //WHEN
        locator.locate(address, "owner");

        //THEN
        assertThat(address.getGeoSource()).isEqualTo(OrderGeoLocator.SOURCE_GEOCODE);
        assertThat(address.getLat()).isEqualTo("18.018000");
    }

    @Test
    void should_fallBackToGeocode_when_fullLinkHasNoCoordinates() {
        //GIVEN - a /place/name link carries no coordinates and is not a short link
        OrderAddress address = address("https://www.google.com/maps/place/ร้านป้าแดง");
        when(mapLinkService.resolve(anyString())).thenThrow(new IllegalArgumentException("not a short link"));
        when(subdistrictRepository.findByCode("S1")).thenReturn(Optional.of(subdistrict));
        when(geocodingService.geocode(anyString()))
                .thenReturn(Optional.of(result("18.01", "99.01", "RANGE_INTERPOLATED", "street_address")));

        //WHEN
        locator.locate(address, "owner");

        //THEN
        assertThat(address.getGeoSource()).isEqualTo(OrderGeoLocator.SOURCE_GEOCODE);
    }

    @Test
    void should_clearCoordinates_when_geocodeIsApproximate() {
        //GIVEN
        OrderAddress address = address(null);
        when(subdistrictRepository.findByCode("S1")).thenReturn(Optional.of(subdistrict));
        when(geocodingService.geocode(anyString()))
                .thenReturn(Optional.of(result("18.0", "99.0", "APPROXIMATE", "street_address")));

        //WHEN
        locator.locate(address, "owner");

        //THEN - old coordinates are cleared too (address changed, they no longer apply)
        assertThat(address.getLat()).isNull();
        assertThat(address.getLng()).isNull();
        assertThat(address.getGeoSource()).isNull();
    }

    @Test
    void should_clearCoordinates_when_geocodeIsOnlyAnArea() {
        //GIVEN - Google matched just the subdistrict, not the house
        OrderAddress address = address(null);
        when(subdistrictRepository.findByCode("S1")).thenReturn(Optional.of(subdistrict));
        when(geocodingService.geocode(anyString()))
                .thenReturn(Optional.of(result("18.0", "99.0", "GEOMETRIC_CENTER", "sublocality", "political")));

        //WHEN
        locator.locate(address, "owner");

        //THEN
        assertThat(address.getGeoSource()).isNull();
    }

    @Test
    void should_clearCoordinates_when_geocodeIsFarFromSubdistrict() {
        //GIVEN - ~22 km away (> 15 km): probably a same-named place elsewhere
        OrderAddress address = address(null);
        when(subdistrictRepository.findByCode("S1")).thenReturn(Optional.of(subdistrict));
        when(geocodingService.geocode(anyString()))
                .thenReturn(Optional.of(result("18.2", "99.0", "ROOFTOP", "street_address")));

        //WHEN
        locator.locate(address, "owner");

        //THEN
        assertThat(address.getGeoSource()).isNull();
    }

    @Test
    void should_returnSubdistrictCentreWithReason_when_previewedGeocodeIsApproximate() {
        //GIVEN
        OrderAddress address = address(null);
        when(subdistrictRepository.findByCode("S1")).thenReturn(Optional.of(subdistrict));
        when(geocodingService.geocode(anyString()))
                .thenReturn(Optional.of(result("18.001", "99.001", "APPROXIMATE", "street_address")));

        //WHEN
        OrderGeoLocator.GeoOutcome outcome = locator.resolve(address, "owner");

        //THEN - the form shows the centre and why, and the address itself is untouched
        assertThat(outcome.source()).isEqualTo(OrderGeoLocator.SOURCE_SUBDISTRICT);
        assertThat(outcome.note()).isEqualTo("APPROXIMATE");
        assertThat(outcome.lat()).isEqualByComparingTo("18.0");
        assertThat(address.getLat()).isEqualTo("1");
    }

    @Test
    void should_reuseEarlierCoordinates_when_sameAddressWasGeocodedBefore() {
        //GIVEN - the owner's earlier order with the same address already has Google coordinates
        OrderAddress address = address(null);
        OrderAddress earlier = OrderAddress.builder().addressId(77L).addressLine("12 ม.3").subdistrictCode("S1")
                .lat("18.010000").lng("99.020000").geoSource(OrderGeoLocator.SOURCE_GEOCODE).build();
        when(subdistrictRepository.findByCode("S1")).thenReturn(Optional.of(subdistrict));
        when(orderAddressRepository.findGeocodedSameAddress("owner", "S1", "12 ม.3")).thenReturn(Optional.of(earlier));

        //WHEN
        locator.locate(address, "owner");

        //THEN - same pin, and no Google call (no quota used)
        assertThat(address.getLat()).isEqualTo("18.010000");
        assertThat(address.getLng()).isEqualTo("99.020000");
        assertThat(address.getGeoSource()).isEqualTo(OrderGeoLocator.SOURCE_GEOCODE);
        verify(geocodingService, never()).geocode(anyString());
    }

    @Test
    void should_clearCoordinatesWithoutFailing_when_geocodingThrows() {
        //GIVEN
        OrderAddress address = address(null);
        when(subdistrictRepository.findByCode("S1")).thenReturn(Optional.of(subdistrict));
        when(geocodingService.geocode(any())).thenThrow(new ExternalApiException("Google down"));

        //WHEN
        locator.locate(address, "owner");

        //THEN
        assertThat(address.getLat()).isNull();
        assertThat(address.getGeoSource()).isNull();
    }
}
