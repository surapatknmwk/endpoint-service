package com.core.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "An order in the admin list (admin.md US-ADM-03)")
public class AdminOrderResponse {

    private Long orderId;

    @Schema(example = "ORD-20261107-AB12")
    private String orderCode;

    @Schema(description = "Owner username (orders.created_by)", example = "owner1")
    private String owner;

    private String customerName;

    private String commodity;

    @Schema(example = "PENDING")
    private String orderStatus;

    private LocalDate appointmentDate;

    private LocalDateTime createdAt;

    @Schema(description = "Latest active job the order is on; null = not on a job")
    private String jobCode;

    @Schema(description = "delivery_status of that job", example = "IN_PROGRESS")
    private String jobDeliveryStatus;
}
