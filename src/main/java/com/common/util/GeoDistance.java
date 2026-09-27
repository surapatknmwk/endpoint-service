package com.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Straight-line (great-circle) distance helper, used as a cheap estimate before asking Google for road distance. */
public final class GeoDistance {

    private static final double EARTH_RADIUS_KM = 6371.0088;

    private GeoDistance() {
    }

    /** Haversine distance in km rounded to 2 decimals, or {@code null} if any coordinate is missing. */
    public static BigDecimal haversineKm(BigDecimal lat1, BigDecimal lng1, BigDecimal lat2, BigDecimal lng2) {
        if (lat1 == null || lng1 == null || lat2 == null || lng2 == null) {
            return null;
        }
        double phi1 = Math.toRadians(lat1.doubleValue());
        double phi2 = Math.toRadians(lat2.doubleValue());
        double dPhi = phi2 - phi1;
        double dLambda = Math.toRadians(lng2.doubleValue() - lng1.doubleValue());
        double a = Math.sin(dPhi / 2) * Math.sin(dPhi / 2)
                + Math.cos(phi1) * Math.cos(phi2) * Math.sin(dLambda / 2) * Math.sin(dLambda / 2);
        double km = 2 * EARTH_RADIUS_KM * Math.asin(Math.min(1.0, Math.sqrt(a)));
        return BigDecimal.valueOf(km).setScale(2, RoundingMode.HALF_UP);
    }
}
