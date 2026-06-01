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

@Tag(name = "Notifications", description = "In-app notifications for incident status changes")
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "List notifications", description = "Returns all notifications for the authenticated user, newest first.")
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
