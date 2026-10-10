package com.core.controller;

import com.common.dto.ApiResponse;
import com.core.dto.OrderRequest;
import com.core.dto.OrderResponse;
import com.core.service.OrderService;
import com.core.service.security.SecurityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/endpoint-core-service/api/orders")
@RequiredArgsConstructor
@Tag(name = "Order", description = "Order management APIs")
public class OrderController {

    private final OrderService orderService;
    private final SecurityService securityService;

    @Operation(summary = "Create a new order", description = "Creates a new order with the provided details. The order always starts as NEW; "
            + "orderStatus in the body is ignored.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "Order created successfully",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid request data",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            )
    })
    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> createOrder(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody OrderRequest request) {
        String username = securityService.extractUsernameFromToken(authHeader);
        OrderResponse data = orderService.createOrder(request, username);
        ApiResponse<OrderResponse> response = ApiResponse.created(data, "Order created successfully");
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Update an order", description = "Updates an existing order by ID. orderStatus in the body is ignored. "
            + "For ASSIGNED/IN_TRANSIT orders the subdistrict/district/province/zip are kept as stored. "
            + "COMPLETED, CANCELLED and FAILED orders cannot be edited.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Order updated successfully",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid request data, order not found, or order is COMPLETED/CANCELLED/FAILED",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            )
    })
    @PutMapping("/{orderId}")
    public ResponseEntity<ApiResponse<OrderResponse>> updateOrder(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader,
            @PathVariable Long orderId,
            @Valid @RequestBody OrderRequest request) {
        String username = securityService.extractUsernameFromToken(authHeader);
        OrderResponse data = orderService.updateOrder(orderId, request, username);
        ApiResponse<OrderResponse> response = ApiResponse.success(data, "Order updated successfully");
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Complete an order", description = "Marks a NEW or PENDING order as completed (delivered). "
            + "Orders on a job (ASSIGNED/IN_TRANSIT) must be completed through the job stop.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Order completed successfully",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Order not found, or its status does not allow this action",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            )
    })
    @PatchMapping("/complete/{orderId}")
    public ResponseEntity<ApiResponse<OrderResponse>> completeOrder(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader,
            @PathVariable Long orderId) {
        String username = securityService.extractUsernameFromToken(authHeader);
        OrderResponse data = orderService.completeOrder(orderId, username);
        ApiResponse<OrderResponse> response = ApiResponse.success(data, "Order completed successfully");
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Cancel an order", description = "Marks a NEW or PENDING order as cancelled. "
            + "Orders on a job (ASSIGNED/IN_TRANSIT) must be cancelled through the job stop.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Order cancelled successfully",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Order not found, or its status does not allow this action",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            )
    })
    @PatchMapping("/cancel/{orderId}")
    public ResponseEntity<ApiResponse<OrderResponse>> cancelOrder(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader,
            @PathVariable Long orderId) {
        String username = securityService.extractUsernameFromToken(authHeader);
        OrderResponse data = orderService.cancelOrder(orderId, username);
        ApiResponse<OrderResponse> response = ApiResponse.success(data, "Order cancelled successfully");
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Delete an order", description = "Deletes an order by ID. Orders on an active job (ASSIGNED/IN_TRANSIT) cannot be deleted.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Order deleted successfully",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Order not found, or its status does not allow this action",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            )
    })
    @DeleteMapping("/{orderId}")
    public ResponseEntity<ApiResponse<Void>> deleteOrder(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader,
            @PathVariable Long orderId) {
        String username = securityService.extractUsernameFromToken(authHeader);
        orderService.deleteOrder(orderId, username);
        ApiResponse<Void> response = ApiResponse.success(null, "Order deleted successfully");
        return ResponseEntity.ok(response);
    }

}
