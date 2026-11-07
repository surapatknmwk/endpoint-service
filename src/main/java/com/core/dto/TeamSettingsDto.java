package com.core.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Team settings of an owner")
public class TeamSettingsDto {

    @Schema(description = "Whether drivers see the price of each stop in this owner's jobs", example = "false")
    @NotNull
    private Boolean showPriceToDriver;
}
