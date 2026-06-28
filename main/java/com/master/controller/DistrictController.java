package com.master.controller;

import com.common.dto.ApiResponse;
import com.master.dto.*;
import com.master.service.DistrictService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/endpoint-master-service/api/districts")
@RequiredArgsConstructor
@Tag(name = "District", description = "District management APIs")
public class DistrictController {

    private final DistrictService districtService;

    @GetMapping("/{id}")
    @Operation(summary = "Get district by ID")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "District found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "District not found")
    })
    public ResponseEntity<ApiResponse<DistrictResponse>> getDistrictById(@PathVariable Long id) {
        DistrictResponse response = districtService.getDistrictById(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/code/{code}")
    @Operation(summary = "Get district by code")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "District found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "District not found")
    })
    public ResponseEntity<ApiResponse<DistrictResponse>> getDistrictByCode(@PathVariable String code) {
        DistrictResponse response = districtService.getDistrictByCode(code);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/province/{provinceId}")
    @Operation(summary = "Get districts by province ID")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Districts retrieved successfully")
    })
    public ResponseEntity<ApiResponse<List<DistrictResponse>>> getDistrictsByProvince(@PathVariable Long provinceId) {
        List<DistrictResponse> response = districtService.getDistrictsByProvince(provinceId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

}
