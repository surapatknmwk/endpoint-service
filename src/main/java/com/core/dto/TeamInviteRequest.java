package com.core.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Invite a driver into the caller's team")
public class TeamInviteRequest {

    @Schema(description = "Username of a driver account; trimmed, compared ignoring case", example = "driver1")
    @NotBlank
    private String username;
}
