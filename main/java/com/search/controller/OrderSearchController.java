package com.search.controller;

import com.common.dto.ApiResponse;
import com.search.dto.request.OrderSearchRequest;
import com.search.dto.response.OrderResponse;
import com.search.dto.response.PageResponse;
import com.search.service.OrderSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/endpoint-search-service/api/search/orders")
@Tag(name = "Order Search", description = "API สำหรับค้นหาข้อมูลคำสั่งซื้อ")
public class OrderSearchController {

    private final OrderSearchService orderSearchService;

    @Operation(summary = "Search orders", description = "Search orders by customer ID and/or platform ID with pagination")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Orders retrieved successfully",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid request data",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            )
    })
    @PostMapping
    public ResponseEntity<ApiResponse<PageResponse<OrderResponse>>> searchOrders(@RequestBody OrderSearchRequest request, HttpServletRequest httpRequest) {
        request.setCreatedBy((String) httpRequest.getAttribute("currentUserId"));
        PageResponse<OrderResponse> data = orderSearchService.searchOrders(request);
        ApiResponse<PageResponse<OrderResponse>> response = ApiResponse.success(data, "Orders retrieved successfully");
        return ResponseEntity.ok(response);
    }
}
