package com.authen.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.ToString;

/** Sign up with LINE (register.md US-REG-07 / 09). No password — LINE proved who the visitor is. */
@Data
public class LineRegisterRequest {

    @ToString.Exclude
    @NotBlank(message = "หมดเวลาสมัคร กรุณาเข้าสู่ระบบด้วย LINE อีกครั้ง")
    private String registrationTicket;

    @NotBlank(message = "กรุณาเลือกประเภทบัญชี")
    @Pattern(regexp = "OWNER|DRIVER", message = "กรุณาเลือกประเภทบัญชี")
    private String accountType;

    @NotBlank(message = "กรุณาตั้งชื่อผู้ใช้")
    @Pattern(regexp = "^[a-z0-9_.-]{4,20}$", message = "ใช้ a–z ตัวเลข . _ - ยาว 4–20 ตัว")
    private String username;

    @NotBlank(message = "กรุณากรอกชื่อ")
    @Size(max = 100, message = "ชื่อยาวเกิน 100 ตัวอักษร")
    private String firstName;

    @Size(max = 100, message = "นามสกุลยาวเกิน 100 ตัวอักษร")
    private String lastName;

    @NotBlank(message = "กรุณากรอกเบอร์โทร")
    @Pattern(regexp = "^0[0-9]{9}$", message = "เบอร์โทรต้องเป็นตัวเลข 10 หลัก ขึ้นต้นด้วย 0")
    private String phone;

    @NotNull(message = "กรุณายอมรับข้อกำหนดและนโยบายความเป็นส่วนตัว")
    @AssertTrue(message = "กรุณายอมรับข้อกำหนดและนโยบายความเป็นส่วนตัว")
    private Boolean acceptTerms;
}
