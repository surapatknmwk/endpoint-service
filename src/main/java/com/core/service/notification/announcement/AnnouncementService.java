package com.core.service.notification.announcement;

import com.core.constant.NotificationType;
import com.core.entity.SystemAnnouncement;
import com.core.repository.NotificationRepository;
import com.core.repository.SystemAnnouncementRepository;
import com.core.service.notification.NotificationPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * ส่งประกาศระบบ (ตาราง system_announcements) เป็นแจ้งเตือน SYSTEM_ANNOUNCEMENT ให้ทุกคนในกลุ่ม
 * — design docs/task/enhanment/systemAnnouncement/design.md §4.1
 * ปลอดภัยถ้ารันซ้ำ: ประกาศที่ส่งแล้วมี published_at และแต่ละคนได้ dedupe key เดียวต่อประกาศ
 * 1 transaction ต่อ 1 ประกาศ — ประกาศหนึ่งพัง ประกาศอื่นยังส่งได้
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnnouncementService {

    private final SystemAnnouncementRepository announcementRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationPublisher notificationPublisher;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public Result run() {
        LocalDateTime now = LocalDateTime.now(clock);

        // ถอนก่อน: ประกาศที่ยกเลิก / หมดอายุแล้ว ไม่ควรค้างในกระดิ่งของคนที่ยังไม่อ่าน
        Integer withdrawn = transactionTemplate.execute(status ->
                notificationRepository.deleteWithdrawnAnnouncements(NotificationType.SYSTEM_ANNOUNCEMENT, now));

        List<Long> dueIds = announcementRepository.findDueIds(now);
        int published = 0;
        int created = 0;
        for (Long id : dueIds) {
            try {
                Integer count = transactionTemplate.execute(status -> publish(id, now));
                if (count != null) {
                    published++;
                    created += count;
                }
            } catch (RuntimeException e) {
                log.error("System announcement {} could not be published", id, e);
            }
        }

        log.info("System announcements: {} due, {} published, {} notification(s) created, {} withdrawn",
                dueIds.size(), published, created, withdrawn);
        return new Result(published, created, withdrawn == null ? 0 : withdrawn);
    }

    /** @return notifications created, or null when the announcement was already taken by another run */
    private Integer publish(Long id, LocalDateTime now) {
        SystemAnnouncement announcement = announcementRepository.lockUnpublished(id).orElse(null);
        if (announcement == null) {
            return null;
        }
        int count = notificationPublisher.publishToAudience(announcement.getAudience(),
                NotificationType.SYSTEM_ANNOUNCEMENT, announcement.getTitle(), announcement.getMessage(),
                announcement.dedupeKey(), announcement.isSendLine());
        announcement.setPublishedAt(now);
        announcement.setRecipientCount(count);
        announcementRepository.save(announcement);
        return count;
    }

    public record Result(int published, int created, int withdrawn) {
    }
}
