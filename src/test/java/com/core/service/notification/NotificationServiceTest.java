package com.core.service.notification;

import com.core.dto.NotificationResponse;
import com.core.entity.Notification;
import com.core.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.SliceImpl;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers {@link NotificationService}: a user can only read their own notifications, marking read is
 * idempotent, and the page size is capped.
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private static final ZoneId BANGKOK = ZoneId.of("Asia/Bangkok");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 21, 9, 0);
    private static final Clock CLOCK = Clock.fixed(NOW.atZone(BANGKOK).toInstant(), BANGKOK);

    @Mock
    private NotificationRepository notificationRepository;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(notificationRepository, CLOCK);
    }

    private Notification notification(LocalDateTime readAt) {
        return Notification.builder().notificationId(41L).recipientUsername("somchai")
                .notificationType("JOB_DUE_TOMORROW").title("t").message("m").dedupeKey("k")
                .readAt(readAt).createdAt(NOW.minusHours(1)).build();
    }

    @Test
    void should_markRead_when_ownerCallsOnUnread() {
        //GIVEN
        Notification unread = notification(null);
        when(notificationRepository.findByNotificationIdAndRecipientUsername(41L, "somchai"))
                .thenReturn(Optional.of(unread));

        //WHEN
        NotificationResponse response = notificationService.markRead(41L, "somchai");

        //THEN
        assertThat(unread.getReadAt()).isEqualTo(NOW);
        assertThat(response.isRead()).isTrue();
        assertThat(response.getReadAt()).isEqualTo(NOW);
    }

    @Test
    void should_keepOriginalReadAt_when_alreadyRead() {
        //GIVEN
        LocalDateTime firstRead = NOW.minusMinutes(30);
        Notification read = notification(firstRead);
        when(notificationRepository.findByNotificationIdAndRecipientUsername(41L, "somchai"))
                .thenReturn(Optional.of(read));

        //WHEN
        NotificationResponse response = notificationService.markRead(41L, "somchai");

        //THEN
        assertThat(response.getReadAt()).isEqualTo(firstRead);
    }

    @Test
    void should_throwNotFound_when_notificationBelongsToAnotherUser() {
        //GIVEN — repository ค้นด้วย id + เจ้าของ: ของคนอื่นจึงหาไม่เจอ
        when(notificationRepository.findByNotificationIdAndRecipientUsername(41L, "intruder"))
                .thenReturn(Optional.empty());

        //WHEN / THEN
        assertThatThrownBy(() -> notificationService.markRead(41L, "intruder"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Notification not found: 41");
    }

    @Test
    void should_capPageSizeAt50_when_requestedSizeIsTooLarge() {
        //GIVEN
        when(notificationRepository.findByRecipientUsername(eq("somchai"), any(Pageable.class)))
                .thenReturn(new SliceImpl<>(List.of()));

        //WHEN
        notificationService.list("somchai", 0, 1000);

        //THEN
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(notificationRepository).findByRecipientUsername(eq("somchai"), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(NotificationService.MAX_PAGE_SIZE);
    }
}
