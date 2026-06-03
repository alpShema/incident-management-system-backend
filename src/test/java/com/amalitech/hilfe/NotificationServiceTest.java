package com.amalitech.hilfe;

import com.amalitech.hilfe.dto.NotificationResponse;
import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.amalitech.hilfe.models.Notification;
import com.amalitech.hilfe.repositories.NotificationRepository;
import com.amalitech.hilfe.services.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock NotificationRepository notificationRepository;
    @InjectMocks NotificationService notificationService;

    private Notification notification(String id, String userId, boolean read) {
        return Notification.builder()
                .id(id).userId(userId).incidentId("inc-1")
                .type("INCIDENT_ASSIGNED").title("Title").message("Msg")
                .read(read).createdAt(Instant.now()).build();
    }

    @Test
    void getNotifications_delegatesToRepository() {
        Notification n = notification("n1", "u1", false);
        when(notificationRepository.findByUserIdOrderByCreatedAtDesc("u1")).thenReturn(List.of(n));

        List<NotificationResponse> result = notificationService.getNotifications("u1");

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().id()).isEqualTo("n1");
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
}
