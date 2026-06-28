package com.master.controller;

import com.common.dto.ApiResponse;
import com.master.dto.*;
import com.master.service.SubdistrictService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/endpoint-master-service/api/subdistricts")
@RequiredArgsConstructor
@Tag(name = "Subdistrict", description = "Subdistrict management APIs")
public class SubdistrictController {

    private final SubdistrictService subdistrictService;

    @GetMapping("/{id}")
    @Operation(summary = "Get subdistrict by ID")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Subdistrict found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Subdistrict not found")
    })
    public ResponseEntity<ApiResponse<SubdistrictResponse>> getSubdistrictById(@PathVariable Long id) {
        SubdistrictResponse response = subdistrictService.getSubdistrictById(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/code/{code}")
    @Operation(summary = "Get subdistrict by code")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Subdistrict found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Subdistrict not found")
    })
    public ResponseEntity<ApiResponse<SubdistrictResponse>> getSubdistrictByCode(@PathVariable String code) {
        SubdistrictResponse response = subdistrictService.getSubdistrictByCode(code);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/district/{districtId}")
    @Operation(summary = "Get subdistricts by district ID")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Subdistricts retrieved successfully")
    })
    public ResponseEntity<ApiResponse<List<SubdistrictResponse>>> getSubdistrictsByDistrict(@PathVariable Long districtId) {
        List<SubdistrictResponse> response = subdistrictService.getSubdistrictsByDistrict(districtId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

}
