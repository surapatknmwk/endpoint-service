package com.core.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "A user as shown in team screens")
public class TeamUserSummary {

    @Schema(description = "User ID", example = "7")
    private Integer userId;

    @Schema(description = "Username", example = "driver1")
    private String username;

    @Schema(description = "First + last name, or the username when no name is set", example = "ประยุทธ ขับดี")
    private String displayName;

    @Schema(description = "Only where the story says so (JobResponse.assignedDriver for the job owner, same team); "
            + "null elsewhere", example = "0891000001")
    private String phone;

    @Schema(description = "LINE profile picture URL; null when the user has not linked LINE (show the initial instead)",
            example = "https://profile.line-scdn.net/0h...")
    private String pictureUrl;

    /** Without a picture (users that have not linked LINE). */
    public TeamUserSummary(Integer userId, String username, String displayName, String phone) {
        this(userId, username, displayName, phone, null);
    }
}
