package com.common.util;

import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * อ่านพิกัดจากลิงก์ Google Maps แบบเต็ม (หรือพิกัดที่พิมพ์ตรงๆ "16.4, 102.8") — กติกาเดียวกับ
 * {@code endpoint-webapp/src/utils/geo.js parseMapLinkCoords} · ลิงก์ย่อ (maps.app.goo.gl) ไม่มีพิกัดในตัว
 * ต้องขยายก่อนด้วย {@link com.master.service.MapLinkService#resolve}
 */
public final class MapLinkCoords {

    private static final String COORD = "(-?\\d{1,2}(?:\\.\\d+)?)\\s*,\\s*(-?\\d{1,3}(?:\\.\\d+)?)";

    /** เรียงตามความแม่น: หมุดของสถานที่ก่อน แล้วค่อยค่าใน query / จุดกลางจอ */
    private static final List<Pattern> PATTERNS = List.of(
            Pattern.compile("!3d(-?\\d+(?:\\.\\d+)?)!4d(-?\\d+(?:\\.\\d+)?)"),
            Pattern.compile("[?&](?:q|query|ll|destination|daddr)=(?:loc:)?" + COORD),
            Pattern.compile("@" + COORD),
            Pattern.compile("^\\s*" + COORD + "\\s*$"));

    public record Point(BigDecimal lat, BigDecimal lng) {
    }

    private MapLinkCoords() {
    }

    public static Optional<Point> parse(String link) {
        if (link == null || link.isBlank()) {
            return Optional.empty();
        }
        String text = link;
        try {
            text = URLDecoder.decode(link, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            // ลิงก์มี % ผิดรูป — ใช้ข้อความเดิม
        }
        for (Pattern pattern : PATTERNS) {
            Matcher m = pattern.matcher(text);
            if (m.find()) {
                double lat = Double.parseDouble(m.group(1));
                double lng = Double.parseDouble(m.group(2));
                if (Math.abs(lat) <= 90 && Math.abs(lng) <= 180) {
                    return Optional.of(new Point(new BigDecimal(m.group(1)), new BigDecimal(m.group(2))));
                }
            }
        }
        return Optional.empty();
    }
}
