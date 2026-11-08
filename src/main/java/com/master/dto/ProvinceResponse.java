package com.master.dto;

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
@Schema(description = "Province response payload")
public class ProvinceResponse {

    @Schema(description = "Province ID", example = "1")
    private Long provinceId;

    @Schema(description = "Province code", example = "10")
    private String code;

    @Schema(description = "Province name", example = "กรุงเทพมหานคร")
    private String name;

    @Schema(description = "false = disabled by the admin: not offered in the pickers, still found by code / id "
            + "(admin.md US-ADM-06) — the webapp shows it as \"(ปิดใช้งาน)\" when it is a record's current value",
            example = "true")
    private Boolean active;

    @Schema(description = "Created timestamp")
    private LocalDateTime createdAt;

    @Schema(description = "Updated timestamp")
    private LocalDateTime updatedAt;
}
