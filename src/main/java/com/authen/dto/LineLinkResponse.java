package com.authen.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LineLinkResponse {
    private boolean linked;
    private String displayName;
    private LocalDateTime linkedAt;
    private boolean canUnlink;
    /** "แจ้งเตือนทาง LINE" switch — omitted when not linked or LINE messaging is off (design lineNotify §7) */
    private Boolean notifyEnabled;
    /** link to add the LINE OA as a friend — omitted when not configured */
    private String addFriendUrl;
}
