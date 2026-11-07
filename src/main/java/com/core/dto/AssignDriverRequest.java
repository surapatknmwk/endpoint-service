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
@Schema(description = "Choose the driver of a job")
public class AssignDriverRequest {

    @Schema(description = "User ID of an ACTIVE member of the caller's team; null = unassign", example = "7",
            nullable = true)
    private Integer driverUserId;
}
