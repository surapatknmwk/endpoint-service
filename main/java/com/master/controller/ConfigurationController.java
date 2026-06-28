package com.master.controller;

import com.common.dto.ApiResponse;
import com.master.dto.ConfigurationDto;
import com.master.service.ConfigurationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/endpoint-master-service/api/configuration")
@RequiredArgsConstructor
@Tag(name = "Configuration", description = "API สำหรับจัดการข้อมูล Configuration")
public class ConfigurationController {

    private final ConfigurationService configurationService;

    @GetMapping("/code/{code}")
    @Operation(summary = "ดึงข้อมูล Configuration ตาม code")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "ดึงข้อมูลสำเร็จ")
    })
    public ResponseEntity<ApiResponse<ConfigurationDto>> findByCode(@PathVariable String code) {
        ConfigurationDto result = configurationService.findByCode(code);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/groupCode/{groupCode}")
    @Operation(summary = "ดึงข้อมูล Configuration ตาม group")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "ดึงข้อมูลสำเร็จ")
    })
    public ResponseEntity<ApiResponse<List<ConfigurationDto>>> findByGroupCode(@PathVariable String groupCode) {
        List<ConfigurationDto> result = configurationService.findByGroupCode(groupCode);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/groupCode/{groupCode}/active")
    @Operation(summary = "ดึงข้อมูล Configuration ที่ active ตาม group")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "ดึงข้อมูลสำเร็จ")
    })
    public ResponseEntity<ApiResponse<List<ConfigurationDto>>> findActiveByGroupCode(@PathVariable String groupCode) {
        List<ConfigurationDto> result = configurationService.findActiveByGroupCode(groupCode);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

}
