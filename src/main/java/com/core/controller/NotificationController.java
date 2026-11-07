package com.core.controller;

import com.common.dto.ApiResponse;
import com.core.dto.NotificationListResponse;
import com.core.dto.NotificationResponse;
import com.core.service.notification.NotificationService;
import com.core.service.security.SecurityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/endpoint-core-service/api/notifications")
@RequiredArgsConstructor
@Tag(name = "Notification", description = "In-app notifications shown under the bell icon")
public class NotificationController {

    private final NotificationService notificationService;
    private final SecurityService securityService;

    @Operation(summary = "List notifications", description = "The caller's notifications, newest first, with the unread count")
    @GetMapping
    public ResponseEntity<ApiResponse<NotificationListResponse>> listNotifications(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        String username = securityService.extractUsernameFromToken(authHeader);
        return ResponseEntity.ok(ApiResponse.success(notificationService.list(username, page, size)));
    }

    @Operation(summary = "Count unread notifications", description = "Polled by the webapp to show the badge on the bell")
    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Map<String, Long>>> countUnread(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader) {
        String username = securityService.extractUsernameFromToken(authHeader);
        return ResponseEntity.ok(ApiResponse.success(Map.of("unreadCount", notificationService.countUnread(username))));
    }

    @Operation(summary = "Mark a notification as read",
            description = "Marking an already-read notification again succeeds and keeps the first read time")
    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<ApiResponse<NotificationResponse>> markRead(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader,
            @PathVariable Long notificationId) {
        String username = securityService.extractUsernameFromToken(authHeader);
        return ResponseEntity.ok(ApiResponse.success(notificationService.markRead(notificationId, username)));
    }

    @Operation(summary = "Mark all notifications as read", description = "Returns how many notifications were unread")
    @PatchMapping("/read-all")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> markAllRead(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader) {
        String username = securityService.extractUsernameFromToken(authHeader);
        return ResponseEntity.ok(ApiResponse.success(Map.of("updated", notificationService.markAllRead(username))));
    }
}
