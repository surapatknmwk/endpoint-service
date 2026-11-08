package com.core.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "A user in the admin list (admin.md US-ADM-01)")
public class AdminUserResponse {

    @Schema(example = "7")
    private Integer userId;

    @Schema(example = "driver1")
    private String username;

    @Schema(description = "First + last name, or the username when no name is set", example = "ประยุทธ ขับดี")
    private String displayName;

    @Schema(description = "roles.role_name", example = "DRIVER")
    private String role;

    @Schema(description = "false = disabled (cannot log in)", example = "true")
    private Boolean active;

    private LocalDateTime lastLogin;

    private LocalDateTime lastSeenAt;

    private LocalDateTime createdAt;
}
