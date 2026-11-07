package com.core.service.notification.reminder;

import com.core.entity.Order;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Builds the text of an "order due soon" notification, e.g.
 * {@code 3 ออเดอร์ยังไม่ได้จัดเข้างาน: สมชาย (พรุ่งนี้), สมหญิง (ศ. 14 พ.ย.) และอีก 1 ราย}
 */
public final class OrderDueMessageBuilder {

    public static final String TITLE = "ออเดอร์ใกล้ถึงวันนัดส่ง";

    private static final int NAMES_SHOWN = 3;
    private static final DateTimeFormatter THAI_SHORT_DATE =
            DateTimeFormatter.ofPattern("EEE d MMM", Locale.of("th", "TH"));

    private OrderDueMessageBuilder() {
    }

    /**
     * @param orders the owner's orders to remind, nearest appointment first
     */
    public static String message(List<Order> orders, LocalDate today) {
        StringBuilder text = new StringBuilder()
                .append(orders.size())
                .append(" ออเดอร์ยังไม่ได้จัดเข้างาน: ");

        List<Order> shown = orders.subList(0, Math.min(NAMES_SHOWN, orders.size()));
        text.append(String.join(", ", shown.stream()
                .map(order -> customerLabel(order) + " (" + dayLabel(order.getAppointmentDate(), today) + ")")
                .toList()));
        int remaining = orders.size() - shown.size();
        if (remaining > 0) {
            text.append(" และอีก ").append(remaining).append(" ราย");
        }
        return text.toString();
    }

    /** "วันนี้" / "พรุ่งนี้" / e.g. "ศ. 14 พ.ย." */
    static String dayLabel(LocalDate appointmentDate, LocalDate today) {
        if (appointmentDate.equals(today)) {
            return "วันนี้";
        }
        if (appointmentDate.equals(today.plusDays(1))) {
            return "พรุ่งนี้";
        }
        return THAI_SHORT_DATE.format(appointmentDate);
    }

    /** The customer name, or the order code when the order has no customer name. */
    private static String customerLabel(Order order) {
        String name = order.getCustomer() != null ? order.getCustomer().getName() : null;
        return name == null || name.isBlank() ? order.getOrderCode() : name.trim();
    }
}
