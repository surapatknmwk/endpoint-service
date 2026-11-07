package com.core.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "One page of the caller's notifications, newest first")
public class NotificationListResponse {

    @Schema(description = "Notifications on this page")
    private List<NotificationResponse> items;

    @Schema(description = "Unread notifications of the caller (all pages)", example = "1")
    private long unreadCount;

    @Schema(description = "Page number, from 0", example = "0")
    private int page;

    @Schema(description = "Page size actually used (max 50)", example = "20")
    private int size;

    @Schema(description = "Whether another page exists", example = "false")
    private boolean hasMore;
}
