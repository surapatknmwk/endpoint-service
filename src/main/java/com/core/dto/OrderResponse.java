package com.core.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Order response payload")
public class OrderResponse {

    @Schema(description = "Order ID", example = "1")
    private Long orderId;

    @Schema(description = "Unique order code", example = "ORD-2024-001")
    private String orderCode;

    @Schema(description = "Customer ID", example = "1")
    private Long customerId;

    @Schema(description = "Customer name", example = "John Doe")
    private String customerName;

    @Schema(description = "Platform ID", example = "1")
    private Long platformId;

    @Schema(description = "Delivery address")
    private OrderAddressResponse address;

    @Schema(description = "Commodity description", example = "Electronics")
    private String commodity;

    @Schema(description = "Package size", example = "Medium")
    private String size;

    @Schema(description = "Order price", example = "1500.00")
    private BigDecimal price;

    @Schema(description = "Package weight in kg", example = "2.5")
    private BigDecimal weight;

    @Schema(description = "Package width in cm", example = "30.0")
    private BigDecimal width;

    @Schema(description = "Package height in cm", example = "20.0")
    private BigDecimal height;

    @Schema(description = "Sequence number for delivery order", example = "1")
    private Integer sequenceNo;

    @Schema(description = "Order details", example = "Handle with care")
    private String detail;

    @Schema(description = "Additional remarks", example = "Leave at door")
    private String remark;

    @Schema(description = "Delivery status", example = "PENDING")
    private String orderStatus;

    @Schema(description = "Record creation timestamp", example = "2024-01-15T10:30:00")
    private LocalDateTime createdAt;

    @Schema(description = "Record last update timestamp", example = "2024-01-15T10:30:00")
    private LocalDateTime updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderAddressResponse {
        private Long addressId;
        private String addressLine;
        private String subdistrictCode;
        private String subdistrictName;
        private String districtCode;
        private String districtName;
        private String provinceCode;
        private String provinceName;
        private String zipCode;
    }

}
