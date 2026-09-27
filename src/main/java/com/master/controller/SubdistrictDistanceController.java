package com.master.controller;

import com.common.dto.ApiResponse;
import com.master.dto.PageResponse;
import com.master.dto.SubdistrictDistanceResponse;
import com.master.service.SubdistrictDistanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/endpoint-master-service/api/subdistrict-distances")
@RequiredArgsConstructor
@Tag(name = "SubdistrictDistance", description = "API สำหรับดึงข้อมูลระยะทางระหว่างตำบล (เฉพาะคู่ในจังหวัดเดียวกัน, คำนวณโดย batch script, อ่านอย่างเดียว)")
public class SubdistrictDistanceController {

    private final SubdistrictDistanceService subdistrictDistanceService;

    @GetMapping
    @Operation(summary = "ดึงรายการระยะทางระหว่างตำบล (แบ่งหน้า, ค้นหาได้)",
            description = "ค้นหาได้แยกจากชื่อตำบล/อำเภอ/จังหวัดต้นทาง และชื่อตำบล/อำเภอ/จังหวัดปลายทาง ข้อมูลมาจาก db/scripts/district_distance_batch.py "
                    + "ครอบคลุมเฉพาะคู่ตำบลที่อยู่จังหวัดเดียวกัน")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "ดึงข้อมูลสำเร็จ")
    })
    public ResponseEntity<ApiResponse<PageResponse<SubdistrictDistanceResponse>>> list(
            @RequestParam(required = false) String originKeyword,
            @RequestParam(required = false) String destinationKeyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "subdistrictDistanceId"));
        PageResponse<SubdistrictDistanceResponse> result = subdistrictDistanceService.list(originKeyword, destinationKeyword, pageable);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
