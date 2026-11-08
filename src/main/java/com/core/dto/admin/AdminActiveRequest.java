package com.core.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Disable (false) or enable (true) an address row")
public class AdminActiveRequest {

    @NotNull(message = "กรุณาระบุสถานะ")
    private Boolean active;
}
