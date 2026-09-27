package com.master.controller;

import com.common.dto.ApiResponse;
import com.master.dto.LocationDistanceRequest;
import com.master.dto.LocationDistanceResponse;
import com.master.dto.PageResponse;
import com.master.service.LocationDistanceService;
import com.core.service.security.SecurityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/endpoint-master-service/api/location-distances")
@RequiredArgsConstructor
@Tag(name = "LocationDistance", description = "API สำหรับจัดการ master data ระยะทางระหว่างตำแหน่ง (อำเภอ/ตำบล)")
public class LocationDistanceController {

    private final LocationDistanceService locationDistanceService;
    private final SecurityService securityService;

    @GetMapping
    @Operation(summary = "ดึงรายการระยะทางทั้งหมด (แบ่งหน้า, ค้นหาได้)")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "ดึงข้อมูลสำเร็จ")
    })
    public ResponseEntity<ApiResponse<PageResponse<LocationDistanceResponse>>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        PageResponse<LocationDistanceResponse> result = locationDistanceService.list(keyword, pageable);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/{id}")
    @Operation(summary = "ดึงข้อมูลระยะทางตาม ID")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "ดึงข้อมูลสำเร็จ"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "ไม่พบข้อมูล")
    })
    public ResponseEntity<ApiResponse<LocationDistanceResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(locationDistanceService.getById(id)));
    }

    @GetMapping("/lookup")
    @Operation(summary = "ค้นหาระยะทางระหว่าง 2 ตำแหน่ง",
            description = "ใช้สำหรับวิเคราะห์/คำนวณระยะทาง โดยจะตรวจสอบทั้งสองทิศทาง (ต้นทาง<->ปลายทาง)")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "พบข้อมูลระยะทาง"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "ไม่พบข้อมูลระยะทาง")
    })
    public ResponseEntity<ApiResponse<LocationDistanceResponse>> lookup(
            @RequestParam Long originDistrictId,
            @RequestParam(required = false) Long originSubdistrictId,
            @RequestParam Long destinationDistrictId,
            @RequestParam(required = false) Long destinationSubdistrictId) {
        LocationDistanceResponse result = locationDistanceService.lookup(
                originDistrictId, originSubdistrictId, destinationDistrictId, destinationSubdistrictId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping
    @Operation(summary = "เพิ่มข้อมูลระยะทางใหม่")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "เพิ่มข้อมูลสำเร็จ"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "ข้อมูลไม่ถูกต้อง")
    })
    public ResponseEntity<ApiResponse<LocationDistanceResponse>> create(
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody LocationDistanceRequest request) {
        String username = authHeader == null ? null : securityService.extractUsernameFromToken(authHeader);
        LocationDistanceResponse result = locationDistanceService.create(request, username);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(result, "เพิ่มข้อมูลระยะทางสำเร็จ"));
    }

    @PutMapping("/{id}")
    @Operation(summary = "แก้ไขข้อมูลระยะทาง")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "แก้ไขข้อมูลสำเร็จ"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "ข้อมูลไม่ถูกต้อง"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "ไม่พบข้อมูล")
    })
    public ResponseEntity<ApiResponse<LocationDistanceResponse>> update(
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable Long id,
            @RequestBody LocationDistanceRequest request) {
        String username = authHeader == null ? null : securityService.extractUsernameFromToken(authHeader);
        LocationDistanceResponse result = locationDistanceService.update(id, request, username);
        return ResponseEntity.ok(ApiResponse.success(result, "แก้ไขข้อมูลระยะทางสำเร็จ"));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "ลบข้อมูลระยะทาง")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "ลบข้อมูลสำเร็จ"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "ไม่พบข้อมูล")
    })
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        locationDistanceService.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null, "ลบข้อมูลระยะทางสำเร็จ"));
    }
}
