package com.authen.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.ToString;

@Data
public class LineCallbackRequest {
    @ToString.Exclude
    @NotBlank(message = "ข้อมูลจาก LINE ไม่ครบ กรุณาลองใหม่อีกครั้ง")
    private String code;

    @ToString.Exclude
    @NotBlank(message = "ข้อมูลจาก LINE ไม่ครบ กรุณาลองใหม่อีกครั้ง")
    private String state;
}
