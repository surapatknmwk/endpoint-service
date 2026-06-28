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
@Schema(description = "Order request payload")
public class OrderRequest {

    @Schema(description = "Customer information")
    private CustomerInfo customer;

    @Schema(description = "Platform Id", example = "FB")
    private Long platformId;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CustomerInfo {
        @Schema(description = "Title ID", example = "1")
        private Integer titleId;

        @Schema(description = "First name", example = "John")
        private String firstName;

        @Schema(description = "Last name", example = "Doe")
        private String lastName;

        @Schema(description = "Full name", example = "John Doe")
        private String name;

        @Schema(description = "Phone number", example = "0812345678")
        private String phone;

        @Schema(description = "Email address", example = "john@example.com")
        private String email;

        @Schema(description = "Notes", example = "VIP customer")
        private String notes;
    }

    @Schema(description = "Delivery address information")
    private AddressInfo address;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AddressInfo {
        @Schema(description = "Address line", example = "123/45 ถนนสุขุมวิท")
        private String addressLine;

        @Schema(description = "Subdistrict Code", example = "470101")
        private String subdistrictCode;

        @Schema(description = "District Code", example = "4701")
        private String districtCode;

        @Schema(description = "Province Code", example = "47")
        private String provinceCode;

        @Schema(description = "Zip code", example = "47000")
        private String zipCode;

        @Schema(description = "Map Link", example = "https://google.map...")
        private String mapLink;
    }

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

    @Schema(description = "Order status", example = "PENDING")
    private String orderStatus;

    @Schema(description = "Expected delivery date", example = "2024-12-31")
    private LocalDateTime deliveryDate;

    
}
