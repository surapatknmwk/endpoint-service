package com.core.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Every field is optional; blank = cleared. subdistrictCode = null clears the whole address.")
public class ProfileUpdateRequest {

    @Size(max = 100)
    private String firstName;

    @Size(max = 100)
    private String lastName;

    @Schema(description = "10 digits starting with 0; dashes and spaces are ignored", example = "089-100-0001")
    @Size(max = 20)
    private String phone;

    @Schema(description = "House number / street — needs a subdistrict", example = "45 ถ.สุขเกษม")
    @Size(max = 255)
    private String addressLine;

    @Schema(description = "Subdistrict code; district and province are derived from it", example = "470101")
    @Size(max = 10)
    private String subdistrictCode;
}
