package com.core.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Candidate order for a job, ranked by distance from the current route point")
public class CandidateOrderResponse {

    @Schema(description = "Order ID", example = "1")
    private Long orderId;

    @Schema(description = "Order code", example = "ORD-20260809-A1B2C3D4")
    private String orderCode;

    @Schema(description = "Customer name", example = "John Doe")
    private String customerName;

    @Schema(description = "Customer phone (owner-only endpoint)", example = "0812345678")
    private String tel;

    @Schema(description = "Delivery address")
    private OrderResponse.OrderAddressResponse address;

    @Schema(description = "What to deliver (order detail)", example = "ข้าวสาร 2 กระสอบ")
    private String detail;

    @Schema(description = "Order status", example = "PENDING")
    private String orderStatus;

    @Schema(description = "Appointment date agreed with the customer; null = no appointment", example = "2026-11-12")
    private LocalDate appointmentDate;

    @Schema(description = "Distance in km from the reference (fromSubdistrictCode) point; null when no distance data exists for this pair", example = "3.42")
    private BigDecimal distanceKm;

    @Schema(description = "Detour in km if this order is inserted between the origin and the specified destination "
            + "(distance(origin->order) + distance(order->destination) - distance(origin->destination)); "
            + "only populated when a destination reference point was given, null otherwise or when any leg is unknown",
            example = "1.15")
    private BigDecimal detourKm;
}
