package com.amalitech.hilfe;

import com.amalitech.hilfe.controllers.NotificationController;
import com.amalitech.hilfe.dto.NotificationResponse;
import com.amalitech.hilfe.dto.PageResponse;
import com.amalitech.hilfe.exceptions.GlobalExceptionHandler;
import com.amalitech.hilfe.models.RoleCode;
import com.amalitech.hilfe.security.Http401AuthenticationEntryPoint;
import com.amalitech.hilfe.security.JwtAuthenticationFilter;
import com.amalitech.hilfe.security.SecurityConfig;
import com.amalitech.hilfe.services.JwtTokenService;
import com.amalitech.hilfe.services.NotificationService;
import com.amalitech.hilfe.services.TokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(NotificationController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class, JwtAuthenticationFilter.class, Http401AuthenticationEntryPoint.class})
@TestPropertySource(properties = "cors.allowed-origins=http://localhost")
class NotificationControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean NotificationService notificationService;
    @MockitoBean TokenService tokenService;

    private UsernamePasswordAuthenticationToken auth() {
        var principal = new JwtTokenService.AuthPrincipal("u1", "u1@test.com", RoleCode.CLIENT);
        return new UsernamePasswordAuthenticationToken(principal, null, List.of(() -> "ROLE_CLIENT"));
    }

    private NotificationResponse stubNotification() {
        return new NotificationResponse("n1", "inc-1", "INCIDENT_STATUS_CHANGED",
                "Status updated", "Incident status changed to RESOLVED", false, Instant.now());
    }

    @Test
    void getNotifications_returns200WithPagedList() throws Exception {
        PageResponse<NotificationResponse> page = new PageResponse<>(
                List.of(stubNotification()), 0, 20, 1L, 1, false, false);
        when(notificationService.getNotifications(eq("u1"), any(Pageable.class))).thenReturn(page);

        mvc.perform(get("/notifications").with(authentication(auth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Notifications retrieved successfully"))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value("n1"))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    void getUnreadCount_returns200WithCount() throws Exception {
        when(notificationService.getUnreadCount("u1")).thenReturn(3L);

        mvc.perform(get("/notifications/unread-count").with(authentication(auth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Unread count retrieved"))
                .andExpect(jsonPath("$.data").value(3));
    }

    @Test
    void markAsRead_returns200WithUpdatedNotification() throws Exception {
        NotificationResponse read = new NotificationResponse("n1", "inc-1", "INCIDENT_STATUS_CHANGED",
                "Status updated", "Incident status changed to RESOLVED", true, Instant.now());
        when(notificationService.markAsRead("u1", "n1")).thenReturn(read);

        mvc.perform(patch("/notifications/n1/read").with(authentication(auth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Notification marked as read"))
                .andExpect(jsonPath("$.data.read").value(true));
    }

    @Test
    void markAllAsRead_returns200() throws Exception {
        mvc.perform(patch("/notifications/read-all").with(authentication(auth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("All notifications marked as read"));

        verify(notificationService).markAllAsRead("u1");
    }
}
