package com.authen.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request object for checking API permission")
public class CheckPermissionRequest {

    @NotBlank(message = "HTTP method is required")
    @Schema(description = "HTTP method (GET, POST, PUT, DELETE, etc.)", example = "GET")
    private String method;

    @NotBlank(message = "API path is required")
    @Schema(description = "API endpoint path", example = "/api/users")
    private String api;
}
