package com.core.service.notification.line;

import java.util.List;
import java.util.Map;

/** The LINE card of a notification: title, message and a "เปิดดู" button (design lineNotify L3). */
public final class LineFlexMessageBuilder {

    // --ep-primary / --ep-ink-3 of endpoint-webapp/src/styles/theme.css (light theme; LINE has no CSS variables)
    private static final String PRIMARY = "#3346C4";
    private static final String MUTED = "#555555";
    private static final int MAX_ALT_TEXT = 400;   // LINE limit
    private static final int MAX_MESSAGE = 1000;   // keeps the card readable

    private LineFlexMessageBuilder() {
    }

    public static Map<String, Object> build(String title, String message, String openUrl) {
        Map<String, Object> body = Map.of(
                "type", "box",
                "layout", "vertical",
                "spacing", "md",
                "contents", List.of(
                        Map.of("type", "text", "text", "ENDPOINT", "size", "xs", "color", PRIMARY, "weight", "bold"),
                        Map.of("type", "text", "text", title, "weight", "bold", "size", "md", "wrap", true),
                        Map.of("type", "text", "text", cut(message, MAX_MESSAGE), "size", "sm", "color", MUTED,
                                "wrap", true)));
        Map<String, Object> footer = Map.of(
                "type", "box",
                "layout", "vertical",
                "contents", List.of(Map.of(
                        "type", "button",
                        "style", "primary",
                        "color", PRIMARY,
                        "height", "sm",
                        "action", Map.of("type", "uri", "label", "เปิดดู", "uri", openUrl))));
        return Map.of(
                "type", "flex",
                "altText", cut(title, MAX_ALT_TEXT),
                "contents", Map.of("type", "bubble", "body", body, "footer", footer));
    }

    /** {webapp}/notification?id=…&openExternalBrowser=1 — opens in the phone's browser, where the user is logged in */
    public static String openUrl(String webappBaseUrl, Long notificationId) {
        String base = webappBaseUrl.endsWith("/") ? webappBaseUrl.substring(0, webappBaseUrl.length() - 1) : webappBaseUrl;
        return base + "/notification?id=" + notificationId + "&openExternalBrowser=1";
    }

    private static String cut(String text, int max) {
        if (text == null || text.isBlank()) {
            return "-";
        }
        return text.length() > max ? text.substring(0, max - 1) + "…" : text;
    }
}
