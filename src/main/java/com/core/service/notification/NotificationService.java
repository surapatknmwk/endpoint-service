package com.core.service.notification;

import com.core.dto.NotificationListResponse;
import com.core.dto.NotificationResponse;
import com.core.entity.Notification;
import com.core.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * The bell: the caller's own notifications only. A notification of another user answers "not found",
 * same as orders and jobs, so ids cannot be probed.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    static final int MAX_PAGE_SIZE = 50;

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("notificationId"));

    private final NotificationRepository notificationRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public NotificationListResponse list(String username, int page, int size) {
        int pageSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        Slice<Notification> slice = notificationRepository.findByRecipientUsername(
                username, PageRequest.of(Math.max(page, 0), pageSize, NEWEST_FIRST));
        return NotificationListResponse.builder()
                .items(slice.map(this::mapToResponse).getContent())
                .unreadCount(countUnread(username))
                .page(slice.getNumber())
                .size(pageSize)
                .hasMore(slice.hasNext())
                .build();
    }

    @Transactional(readOnly = true)
    public long countUnread(String username) {
        return notificationRepository.countByRecipientUsernameAndReadAtIsNull(username);
    }

    /** Marking an already-read notification again succeeds and keeps the first read time. */
    @Transactional
    public NotificationResponse markRead(Long notificationId, String username) {
        Notification notification = notificationRepository
                .findByNotificationIdAndRecipientUsername(notificationId, username)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found: " + notificationId));
        if (notification.getReadAt() == null) {
            notification.setReadAt(LocalDateTime.now(clock));
        }
        return mapToResponse(notification);
    }

    @Transactional
    public int markAllRead(String username) {
        return notificationRepository.markAllRead(username, LocalDateTime.now(clock));
    }

    @Transactional
    public int deleteOlderThan(int retentionDays) {
        return notificationRepository.deleteCreatedBefore(LocalDateTime.now(clock).minusDays(retentionDays));
    }

    private NotificationResponse mapToResponse(Notification notification) {
        return NotificationResponse.builder()
                .notificationId(notification.getNotificationId())
                .type(notification.getNotificationType())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .jobId(notification.getJobId())
                .targetDate(notification.getTargetDate())
                .read(notification.getReadAt() != null)
                .readAt(notification.getReadAt())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}
