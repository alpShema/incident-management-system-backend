package com.amalitech.hilfe;

import com.amalitech.hilfe.dto.NotificationResponse;
import com.amalitech.hilfe.dto.PageResponse;
import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.amalitech.hilfe.models.Notification;
import com.amalitech.hilfe.repositories.NotificationRepository;
import com.amalitech.hilfe.services.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-06-29T14:00:00Z");

    @Mock NotificationRepository notificationRepository;
    @InjectMocks NotificationService notificationService;

    private Notification notification(String id, String userId, boolean read) {
        return Notification.builder()
                .id(id).userId(userId).incidentId("inc-1")
                .type("INCIDENT_ASSIGNED").title("Title").message("Msg")
                .read(read).createdAt(FIXED_NOW).build();
    }

    @Test
    void getNotifications_delegatesToRepository() {
        Notification n = notification("n1", "u1", false);
        Pageable pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"));
        when(notificationRepository.findByUserIdOrderByCreatedAtDesc("u1", pageable))
                .thenReturn(new PageImpl<>(List.of(n), pageable, 1));

        PageResponse<NotificationResponse> result = notificationService.getNotifications("u1", pageable);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().id()).isEqualTo("n1");
        assertThat(result.totalElements()).isEqualTo(1);
    }

    @Test
    void getNotifications_nullReadFilter_returnsAllNotifications() {
        Notification n = notification("n1", "u1", false);
        Pageable pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"));
        when(notificationRepository.findByUserIdOrderByCreatedAtDesc("u1", pageable))
                .thenReturn(new PageImpl<>(List.of(n), pageable, 1));

        PageResponse<NotificationResponse> result = notificationService.getNotifications("u1", pageable, null);

        assertThat(result.items()).hasSize(1);
        verify(notificationRepository, never()).findByUserIdAndReadOrderByCreatedAtDesc(any(), anyBoolean(), any());
    }

    @Test
    void getNotifications_readFalse_returnsOnlyUnreadNotifications() {
        Notification unread = notification("n1", "u1", false);
        Pageable pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"));
        when(notificationRepository.findByUserIdAndReadOrderByCreatedAtDesc("u1", false, pageable))
                .thenReturn(new PageImpl<>(List.of(unread), pageable, 1));

        PageResponse<NotificationResponse> result = notificationService.getNotifications("u1", pageable, false);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().read()).isFalse();
        verify(notificationRepository, never()).findByUserIdOrderByCreatedAtDesc(any(), any());
    }

    @Test
    void getNotifications_readTrue_returnsOnlyReadNotifications() {
        Notification read = notification("n1", "u1", true);
        Pageable pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"));
        when(notificationRepository.findByUserIdAndReadOrderByCreatedAtDesc("u1", true, pageable))
                .thenReturn(new PageImpl<>(List.of(read), pageable, 1));

        PageResponse<NotificationResponse> result = notificationService.getNotifications("u1", pageable, true);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().read()).isTrue();
    }

    @Test
    void getUnreadCount_delegatesToRepository() {
        when(notificationRepository.countByUserIdAndReadFalse("u1")).thenReturn(4L);
        assertThat(notificationService.getUnreadCount("u1")).isEqualTo(4L);
    }

    @Test
    void markAsRead_ownerMarksNotification_setsReadTrue() {
        Notification n = notification("n1", "u1", false);
        when(notificationRepository.findById("n1")).thenReturn(Optional.of(n));
        when(notificationRepository.save(any())).thenReturn(n);

        notificationService.markAsRead("u1", "n1");

        assertThat(n.isRead()).isTrue();
        verify(notificationRepository).save(n);
    }

    @Test
    void markAsRead_notFound_throws404() {
        when(notificationRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markAsRead("u1", "missing"))
                .isInstanceOf(ArmsAuthException.class)
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(404);
    }

    @Test
    void markAsRead_differentUser_throws404() {
        Notification n = notification("n1", "u1", false);
        when(notificationRepository.findById("n1")).thenReturn(Optional.of(n));

        assertThatThrownBy(() -> notificationService.markAsRead("u2", "n1"))
                .isInstanceOf(ArmsAuthException.class)
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(404);
    }

    @Test
    void markAllAsRead_delegatesToRepository() {
        notificationService.markAllAsRead("u1");
        verify(notificationRepository).markAllReadByUserId("u1");
    }

    @Test
    void clearAll_delegatesToRepository() {
        notificationService.clearAll("u1");
        verify(notificationRepository).deleteAllByUserId("u1");
    }
}
