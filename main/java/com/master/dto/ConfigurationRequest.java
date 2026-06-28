package com.master.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request for creating/updating configuration")
public class ConfigurationRequest {

    @NotBlank(message = "Code is required")
    @Size(max = 10, message = "Code must not exceed 10 characters")
    @Schema(description = "Code", example = "CFG001", requiredMode = Schema.RequiredMode.REQUIRED)
    private String code;

    @Size(max = 100, message = "Group must not exceed 100 characters")
    @Schema(description = "Group", example = "SYSTEM")
    private String group;

    @Size(max = 100, message = "Name must not exceed 100 characters")
    @Schema(description = "Name", example = "Max Upload Size")
    private String name;

    @Size(max = 100, message = "Value 1 must not exceed 100 characters")
    @Schema(description = "Value 1", example = "100")
    private String value1;

    @Size(max = 100, message = "Value 2 must not exceed 100 characters")
    @Schema(description = "Value 2", example = "MB")
    private String value2;

    @Size(max = 100, message = "Value 3 must not exceed 100 characters")
    @Schema(description = "Value 3", example = "")
    private String value3;

    @Size(max = 1, message = "Status must be 1 character")
    @Schema(description = "Status (A=Active, I=Inactive)", example = "A")
    private String status;
}
