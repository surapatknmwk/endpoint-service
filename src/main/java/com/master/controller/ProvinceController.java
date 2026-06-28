package com.master.controller;

import com.common.dto.ApiResponse;
import com.master.dto.*;
import com.master.service.ProvinceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/endpoint-master-service/api/provinces")
@RequiredArgsConstructor
@Tag(name = "Province", description = "Province management APIs")
public class ProvinceController {

    private final ProvinceService provinceService;

    @GetMapping("/{id}")
    @Operation(summary = "Get province by ID")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Province found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Province not found")
    })
    public ResponseEntity<ApiResponse<ProvinceResponse>> getProvinceById(@PathVariable Long id) {
        ProvinceResponse response = provinceService.getProvinceById(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/code/{code}")
    @Operation(summary = "Get province by code")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Province found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Province not found")
    })
    public ResponseEntity<ApiResponse<ProvinceResponse>> getProvinceByCode(@PathVariable String code) {
        ProvinceResponse response = provinceService.getProvinceByCode(code);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping
    @Operation(summary = "Get all provinces")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Provinces retrieved successfully")
    })
    public ResponseEntity<ApiResponse<List<ProvinceResponse>>> getAllProvinces() {
        List<ProvinceResponse> response = provinceService.getAllProvinces();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/search")
    @Operation(summary = "Search provinces with pagination")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Search completed successfully")
    })
    public ResponseEntity<ApiResponse<PageResponse<ProvinceResponse>>> searchProvinces(@RequestBody SearchRequest request) {
        PageResponse<ProvinceResponse> response = provinceService.searchProvinces(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
    
}
