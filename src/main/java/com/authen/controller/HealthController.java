package com.authen.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/endpoint-authen-service/health")
@Tag(name = "Health Check", description = "Service health monitoring")
public class HealthController {

    @Operation(
            summary = "Health check",
            description = "Check if the service is running and healthy"
    )
    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "endpoint-authen-service");
        response.put("timestamp", LocalDateTime.now());
        return ResponseEntity.ok(response);
    }

}
