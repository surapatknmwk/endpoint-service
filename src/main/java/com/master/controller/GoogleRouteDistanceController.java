package com.master.controller;

import com.common.dto.ApiResponse;
import com.master.dto.GoogleRouteDistanceResponse;
import com.master.dto.GoogleRoutesUsageResponse;
import com.master.service.GoogleRoutesService;
import com.master.service.GoogleRoutesUsageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * แยกออกมาจาก {@link SubdistrictDistanceController} เป็นกลุ่ม Swagger ของตัวเอง
 * เพราะ endpoint ในนี้ยิงออกไปเรียก Google Routes API จริงๆ (มีค่าใช้จ่าย/ต้องมี
 * API key/พึ่ง network ภายนอก) ต่างจาก endpoint อื่นในกลุ่ม SubdistrictDistance ที่
 * อ่านจากข้อมูลในระบบเราเองล้วนๆ -- URL path ยังอยู่ใต้ /subdistrict-distances เดิม
 * ไม่เปลี่ยน เปลี่ยนแค่การจัดกลุ่มใน Swagger UI
 */
@RestController
@RequestMapping("/endpoint-master-service/api/subdistrict-distances")
@RequiredArgsConstructor
@Tag(name = "GoogleRoutes", description = "ระยะทาง/เวลาเดินทางจริงตามถนนระหว่างตำบล 2 แห่ง (รองรับข้ามจังหวัด) "
        + "ดึงจาก Google Routes API (computeRoutes) โดยตรง -- ต้องตั้งค่า GOOGLE_MAPS_API_KEY (environment variable) "
        + "ให้เป็น API key ที่เปิดใช้งาน Routes API แล้ว")
public class GoogleRouteDistanceController {

    private final GoogleRoutesService googleRoutesService;
    private final GoogleRoutesUsageService googleRoutesUsageService;

    @GetMapping("/google-distance")
    @Operation(summary = "ระยะทาง/เวลาเดินทางจริงตามถนน จาก Google Routes API (รองรับข้ามจังหวัด)",
            description = "ยิง request ไปที่ Google Routes API (computeRoutes) โดยตรงเพื่อขอระยะทาง/เวลาเดินทางจริงตามถนน "
                    + "ระหว่างตำบลต้นทาง/ปลายทาง ต้องตั้งค่า GOOGLE_MAPS_API_KEY (environment variable) ให้เป็น API key "
                    + "ที่เปิดใช้งาน Routes API แล้ว ใช้ routingPreference=TRAFFIC_UNAWARE เพื่อให้ตกอยู่ใน Essentials tier (ถูกกว่า)")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "เรียก Google Routes API สำเร็จ"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "originCode/destinationCode ว่าง หรือตำบลนั้นยังไม่มี lat/lng"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "ไม่พบตำบลตามรหัสที่ระบุ"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "ต้องเรียก Google จริงแต่โควตารายวัน (google.maps.daily-request-limit) เต็มแล้ว"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "502", description = "เรียก Google Routes API ไม่สำเร็จ (ไม่ได้ตั้งค่า API key, network error, หรือ Google ตอบกลับ error)")
    })
    public ResponseEntity<ApiResponse<GoogleRouteDistanceResponse>> googleDistance(
            @RequestParam String originCode,
            @RequestParam String destinationCode) {
        GoogleRouteDistanceResponse result = googleRoutesService.findRoute(originCode, destinationCode);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/google-distance/usage")
    @Operation(summary = "สถิติการยิง Google Routes API จริงต่อวัน เทียบกับโควตารายวัน",
            description = "แสดงโควตารายวัน (google.maps.daily-request-limit), จำนวนที่ยิงไปแล้ววันนี้/ที่เหลือ และประวัติรายวัน "
                    + "(เฉพาะการยิง Google จริง ไม่นับผลที่อ่านจาก database) วันนับตามเวลา Asia/Bangkok")
    public ResponseEntity<ApiResponse<GoogleRoutesUsageResponse>> usage(
            @RequestParam(required = false) Integer days) {
        return ResponseEntity.ok(ApiResponse.success(googleRoutesUsageService.getUsage(days)));
    }
}
