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

    @Operation(summary = "Create a new order", description = "Creates a new order with the provided details")
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
            @RequestBody OrderRequest request) {
        String username = securityService.extractUsernameFromToken(authHeader);
        OrderResponse data = orderService.createOrder(request, username);
        ApiResponse<OrderResponse> response = ApiResponse.created(data, "Order created successfully");
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Update an order", description = "Updates an existing order by ID")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Order updated successfully",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid request data",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Order not found",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            )
    })
    @PutMapping("/{orderId}")
    public ResponseEntity<ApiResponse<OrderResponse>> updateOrder(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader,
            @PathVariable Long orderId,
            @RequestBody OrderRequest request) {
        String username = securityService.extractUsernameFromToken(authHeader);
        OrderResponse data = orderService.updateOrder(orderId, request, username);
        ApiResponse<OrderResponse> response = ApiResponse.success(data, "Order updated successfully");
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Complete an order", description = "Marks an order as completed (delivered)")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Order completed successfully",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Order not found",
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

    @Operation(summary = "Delete an order", description = "Deletes an order by ID")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Order deleted successfully",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Order not found",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            )
    })
    @DeleteMapping("/{orderId}")
    public ResponseEntity<ApiResponse<Void>> deleteOrder(@PathVariable Long orderId) {
        orderService.deleteOrder(orderId);
        ApiResponse<Void> response = ApiResponse.success(null, "Order deleted successfully");
        return ResponseEntity.ok(response);
    }

}
