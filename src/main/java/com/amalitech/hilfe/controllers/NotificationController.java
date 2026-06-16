package com.amalitech.hilfe.controllers;

import com.amalitech.hilfe.dto.ApiResponse;
import com.amalitech.hilfe.dto.NotificationResponse;
import com.amalitech.hilfe.services.JwtTokenService;
import com.amalitech.hilfe.services.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(
        name = "Notifications",
        description = """
                In-app notifications for incident status changes. Notifications are delivered live over \
                WebSocket (STOMP + SockJS) in addition to being persisted for retrieval via the REST endpoints below.

                **Live delivery (WebSocket):**
                - Connect endpoint: `ws://<host>/ws` (SockJS-compatible). JWT is required — pass it as the \
                `Authorization: Bearer <token>` header during the STOMP CONNECT handshake.
                - Subscribe destination: `/topic/users/{userId}/notifications` — substitute the authenticated \
                user's id. Each message pushed on this topic is a `NotificationResponse` payload (same shape \
                returned by `GET /notifications`).
                - Application prefix (client→server): `/app` (not used for notifications — they are server-push only).
                - Use `GET /notifications` for the initial load and re-sync after a reconnect; use the WebSocket \
                topic for live updates while connected."""
)
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(
            summary = "List notifications",
            description = "Returns all notifications for the authenticated user, newest first. "
                    + "Use this endpoint for the initial load and to re-sync after a WebSocket reconnect. "
                    + "For live updates, subscribe to `/topic/users/{userId}/notifications` over the `/ws` "
                    + "WebSocket endpoint (see the Notifications tag description for the full contract)."
    )
    @GetMapping
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> getNotifications(
            @AuthenticationPrincipal JwtTokenService.AuthPrincipal principal
    ) {
        return ResponseEntity.ok(ApiResponse.success("Notifications retrieved successfully",
                notificationService.getNotifications(principal.userId())));
    }

    @Operation(summary = "Unread count", description = "Returns the number of unread notifications for the authenticated user.")
    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Long>> getUnreadCount(
            @AuthenticationPrincipal JwtTokenService.AuthPrincipal principal
    ) {
        return ResponseEntity.ok(ApiResponse.success("Unread count retrieved",
                notificationService.getUnreadCount(principal.userId())));
    }

    @Operation(summary = "Mark one as read", description = "Marks a single notification as read.")
    @PatchMapping("/{id}/read")
    public ResponseEntity<ApiResponse<NotificationResponse>> markAsRead(
            @AuthenticationPrincipal JwtTokenService.AuthPrincipal principal,
            @PathVariable String id
    ) {
        return ResponseEntity.ok(ApiResponse.success("Notification marked as read",
                notificationService.markAsRead(principal.userId(), id)));
    }

    @Operation(summary = "Mark all as read", description = "Marks all notifications for the authenticated user as read.")
    @PatchMapping("/read-all")
    public ResponseEntity<ApiResponse<Void>> markAllAsRead(
            @AuthenticationPrincipal JwtTokenService.AuthPrincipal principal
    ) {
        notificationService.markAllAsRead(principal.userId());
        return ResponseEntity.ok(ApiResponse.success("All notifications marked as read", null));
    }
}
