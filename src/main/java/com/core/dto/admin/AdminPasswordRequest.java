package com.core.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "New password set by the admin (admin.md US-ADM-05)")
public class AdminPasswordRequest {

    @ToString.Exclude
    @NotBlank(message = "กรุณากรอกรหัสผ่านใหม่")
    @Size(min = 6, max = 100, message = "รหัสผ่านต้องยาว 6–100 ตัวอักษร")
    @Schema(example = "new-pass-123")
    private String newPassword;
}
