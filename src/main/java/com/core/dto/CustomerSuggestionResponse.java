package com.core.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "A previous customer + address of the caller, to prefill the order form")
public class CustomerSuggestionResponse {

    @Schema(description = "Customer name", example = "สมศรี จันทร์หอม")
    private String customerName;

    @Schema(description = "Customer phone", example = "0904097806")
    private String tel;

    @Schema(description = "Platform (configuration id) of the latest such order", example = "1")
    private Long platformId;

    @Schema(description = "Delivery address of that order, including its Google Maps link")
    private OrderResponse.OrderAddressResponse address;

    @Schema(description = "When the latest order for this customer + address was created")
    private LocalDateTime lastOrderAt;
}
