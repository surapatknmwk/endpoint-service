package com.authen.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** PATCH /auth/line/notify — the "แจ้งเตือนทาง LINE" switch on the Account page */
@Data
public class LineNotifyRequest {
    @NotNull(message = "กรุณาระบุเปิดหรือปิดการแจ้งเตือน")
    private Boolean enabled;
}
