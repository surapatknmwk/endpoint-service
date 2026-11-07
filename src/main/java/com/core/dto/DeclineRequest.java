package com.core.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Body of declining a job request — both fields optional (design assignConfirm F2). */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Why the driver declines; both optional")
public class DeclineRequest {

    @Schema(description = "NOT_AVAILABLE_DATE, TOO_FAR, VEHICLE, OTHER or null", example = "TOO_FAR")
    private String reason;

    @Size(max = 200)
    @Schema(description = "Free text, only with reason OTHER", example = "ฝนตกหนัก ถนนปิด")
    private String note;
}
