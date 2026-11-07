package com.core.service.notification.reminder;

import com.core.constant.NotificationType;
import com.core.entity.Notification;
import com.core.entity.Order;
import com.core.repository.OrderRepository;
import com.core.service.notification.NotificationPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * แจ้งเตือนเจ้าของว่ามี order ที่ยังไม่ได้จัดเข้างาน (NEW / PENDING) ใกล้ถึงวันนัดส่ง (วันนี้ ถึงอีก 7 วัน)
 * รวมเป็น 1 แจ้งเตือนต่อเจ้าของต่อวัน และแต่ละ order เตือนครั้งเดียวต่อวันนัด (appointment_reminded_date)
 * ปลอดภัยถ้ารันซ้ำในวันเดียวกัน: ชน dedupe key = ไม่สร้าง และไม่ทำเครื่องหมาย จึงไปรวมในรอบวันถัดไป
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderDueReminderService {

    static final int DAYS_AHEAD = 7;

    private final OrderRepository orderRepository;
    private final NotificationPublisher notificationPublisher;
    private final Clock clock;

    @Transactional
    public Result run() {
        LocalDate today = LocalDate.now(clock);

        Map<String, List<Order>> ordersByOwner = orderRepository
                .findAppointmentsToRemind(today, today.plusDays(DAYS_AHEAD)).stream()
                .collect(Collectors.groupingBy(Order::getCreatedBy, LinkedHashMap::new, Collectors.toList()));

        int created = 0;
        int reminded = 0;
        for (Map.Entry<String, List<Order>> entry : ordersByOwner.entrySet()) {
            List<Order> orders = entry.getValue();
            if (!publish(entry.getKey(), orders, today)) {
                continue;
            }
            created++;
            reminded += orderRepository.markAppointmentReminded(orders.stream().map(Order::getOrderId).toList());
        }

        log.info("Order due reminders for {}: {} owner(s), {} created, {} order(s) reminded",
                today, ordersByOwner.size(), created, reminded);
        return new Result(created, reminded);
    }

    private boolean publish(String owner, List<Order> orders, LocalDate today) {
        return notificationPublisher.publish(Notification.builder()
                .recipientUsername(owner)
                .notificationType(NotificationType.ORDER_DUE_SOON)
                .title(OrderDueMessageBuilder.TITLE)
                .message(OrderDueMessageBuilder.message(orders, today))
                .targetDate(today)
                .dedupeKey(NotificationType.ORDER_DUE_SOON + ":" + today)
                .build());
    }

    /**
     * @param created  notifications created (one per owner)
     * @param reminded orders marked as reminded
     */
    public record Result(int created, int reminded) {
    }
}
