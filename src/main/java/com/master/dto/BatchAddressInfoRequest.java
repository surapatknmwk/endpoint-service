package com.master.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Batch request for multiple address info lookups")
public class BatchAddressInfoRequest {

    @Schema(description = "List of address info requests")
    private List<AddressInfoRequest> addresses;
}
