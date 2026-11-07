package com.core.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Driver availability")
public class AvailabilityRequest {

    @Schema(description = "AVAILABLE, BUSY or OFF", example = "BUSY")
    @NotBlank
    private String status;
}
