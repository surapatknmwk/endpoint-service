package com.master.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Configuration data")
public class ConfigurationDto {

    @Schema(description = "Config ID", example = "1")
    private Long configId;

    @Schema(description = "Code", example = "CFG001")
    private String code;

    @Schema(description = "Group", example = "SYSTEM")
    private String group;

    @Schema(description = "Name", example = "Max Upload Size")
    private String name;

    @Schema(description = "Value 1", example = "100")
    private String value1;

    @Schema(description = "Value 2", example = "MB")
    private String value2;

    @Schema(description = "Value 3", example = "")
    private String value3;

    @Schema(description = "Status (A=Active, I=Inactive)", example = "A")
    private String status;
}
