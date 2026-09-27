package com.common.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class GeoDistanceTest {

    @Test
    void should_returnStraightLineDistance_when_coordinatesAreValid() {
        //GIVEN Bangkok and Chiang Mai (about 585 km apart in a straight line)
        BigDecimal bangkokLat = new BigDecimal("13.7563");
        BigDecimal bangkokLng = new BigDecimal("100.5018");
        BigDecimal chiangMaiLat = new BigDecimal("18.7883");
        BigDecimal chiangMaiLng = new BigDecimal("98.9853");

        //WHEN
        BigDecimal km = GeoDistance.haversineKm(bangkokLat, bangkokLng, chiangMaiLat, chiangMaiLng);

        //THEN
        assertThat(km).isBetween(new BigDecimal("575"), new BigDecimal("595"));
    }

    @Test
    void should_returnZero_when_bothPointsAreTheSame() {
        //GIVEN
        BigDecimal lat = new BigDecimal("17.164968");
        BigDecimal lng = new BigDecimal("104.133823");

        //WHEN
        BigDecimal km = GeoDistance.haversineKm(lat, lng, lat, lng);

        //THEN
        assertThat(km).isEqualByComparingTo("0");
    }

    @Test
    void should_returnNull_when_anyCoordinateIsMissing() {
        //GIVEN
        BigDecimal lat = new BigDecimal("17.0");
        BigDecimal lng = new BigDecimal("104.0");

        //WHEN + THEN
        assertThat(GeoDistance.haversineKm(null, lng, lat, lng)).isNull();
        assertThat(GeoDistance.haversineKm(lat, lng, lat, null)).isNull();
    }
}
