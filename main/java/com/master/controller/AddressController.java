package com.master.controller;

import com.common.dto.ApiResponse;
import com.master.dto.*;
import com.master.service.AddressService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/endpoint-master-service/api/address")
@RequiredArgsConstructor
@Tag(name = "Address", description = "API สำหรับดึงข้อมูลที่อยู่แบบรวม (จังหวัด, อำเภอ, ตำบล) - ลดจำนวน request จากหลายครั้งเหลือครั้งเดียว")
public class AddressController {

    private final AddressService addressService;

    @GetMapping("/info")
    @Operation(summary = "ดึงข้อมูลที่อยู่ตาม code",
            description = "ดึงข้อมูลจังหวัด, อำเภอ, ตำบล ในครั้งเดียว")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "ดึงข้อมูลที่อยู่สำเร็จ")
    })
    public ResponseEntity<ApiResponse<AddressInfoDto>> getAddressInfo(
            @RequestParam(required = false) String provinceCode,
            @RequestParam(required = false) String districtCode,
            @RequestParam(required = false) String subdistrictCode) {
        AddressInfoDto response = addressService.getAddressInfo(provinceCode, districtCode, subdistrictCode);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/info-by-id")
    @Operation(summary = "ดึงข้อมูลที่อยู่ตาม ID",
            description = "ดึงข้อมูลจังหวัด, อำเภอ, ตำบล ในครั้งเดียวโดยใช้ ID")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "ดึงข้อมูลที่อยู่สำเร็จ")
    })
    public ResponseEntity<ApiResponse<AddressInfoDto>> getAddressInfoById(
            @RequestParam(required = false) Long provinceId,
            @RequestParam(required = false) Long districtId,
            @RequestParam(required = false) Long subdistrictId) {
        AddressInfoDto response = addressService.getAddressInfoById(provinceId, districtId, subdistrictId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/info")
    @Operation(summary = "ดึงข้อมูลที่อยู่ผ่าน request body",
            description = "ดึงข้อมูลจังหวัด, อำเภอ, ตำบล ในครั้งเดียว")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "ดึงข้อมูลที่อยู่สำเร็จ")
    })
    public ResponseEntity<ApiResponse<AddressInfoDto>> getAddressInfoPost(@RequestBody AddressInfoRequest request) {
        AddressInfoDto response = addressService.getAddressInfo(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/info/batch")
    @Operation(summary = "ดึงข้อมูลที่อยู่หลายรายการ",
            description = "ดึงข้อมูลที่อยู่หลายรายการในครั้งเดียว - เพิ่มประสิทธิภาพโดยลด query จาก N*3 เหลือ 3 ครั้ง")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "ดึงข้อมูลที่อยู่แบบ batch สำเร็จ")
    })
    public ResponseEntity<ApiResponse<List<AddressInfoDto>>> getAddressInfoBatch(@RequestBody BatchAddressInfoRequest request) {
        List<AddressInfoDto> response = addressService.getAddressInfoBatch(request.getAddresses());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/info/subdistrict/{subdistrictCode}")
    @Operation(summary = "ดึงข้อมูลที่อยู่ทั้งหมดจากรหัสตำบล",
            description = "ดึงข้อมูลที่อยู่ครบถ้วน (จังหวัด, อำเภอ, ตำบล) จากรหัสตำบลเพียงอย่างเดียว")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "ดึงข้อมูลที่อยู่ทั้งหมดสำเร็จ")
    })
    public ResponseEntity<ApiResponse<AddressInfoDto>> getFullAddressInfoBySubdistrict(@PathVariable String subdistrictCode) {
        AddressInfoDto response = addressService.getFullAddressInfoBySubdistrict(subdistrictCode);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/info/subdistrict/batch")
    @Operation(summary = "ดึงข้อมูลที่อยู่ทั้งหมดจากรหัสตำบลหลายรายการ",
            description = "ดึงข้อมูลที่อยู่ครบถ้วนสำหรับหลายตำบลในครั้งเดียว")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "ดึงข้อมูลที่อยู่แบบ batch สำเร็จ")
    })
    public ResponseEntity<ApiResponse<List<AddressInfoDto>>> getFullAddressInfoBySubdistrictBatch(@RequestBody List<String> subdistrictCodes) {
        List<AddressInfoDto> response = addressService.getFullAddressInfoBySubdistrictBatch(subdistrictCodes);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/info/subdistrict-code/batch")
    @Operation(summary = "ดึงข้อมูลที่อยู่ทั้งหมดจาก ID ตำบลหลายรายการ",
            description = "ดึงข้อมูลที่อยู่ครบถ้วนสำหรับหลายตำบลในครั้งเดียวโดยใช้ ID")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "ดึงข้อมูลที่อยู่แบบ batch สำเร็จ")
    })
    public ResponseEntity<ApiResponse<List<AddressInfoDto>>> getFullAddressInfoBySubdistrictCodeBatch(@RequestBody List<String> subdistrictCodes) {
        List<AddressInfoDto> response = addressService.getFullAddressInfoBySubdistrictCodeBatch(subdistrictCodes);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
