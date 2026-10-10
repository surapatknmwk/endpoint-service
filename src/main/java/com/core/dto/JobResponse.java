package com.core.dto;

import com.core.dto.master.AddressInfoDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Job response payload")
public class JobResponse {

    @Schema(description = "Job ID", example = "1")
    private Long jobId;

    @Schema(description = "Unique job code", example = "JOB-20260809-A1B2C3D4")
    private String jobCode;

    @Schema(description = "Job name", example = "รอบส่งเช้า อำเภอ A - อำเภอ B")
    private String jobName;

    @Schema(description = "Origin (starting point) location")
    private AddressInfoDto origin;

    @Schema(description = "Total number of orders in this job", example = "5")
    private Integer totalOrders;

    @Schema(description = "Delivery status", example = "NEW")
    private String deliveryStatus;

    @Schema(description = "Scheduled delivery date")
    private LocalDateTime scheduledDate;

    @Schema(description = "Record creation timestamp")
    private LocalDateTime createdAt;

    @Schema(description = "Ordered list of delivery stops")
    private List<RoutingItem> routings;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RoutingItem {
        private Long orderId;
        private String orderCode;
        private String customerName;
        private OrderResponse.OrderAddressResponse address;
        private Integer sequenceNo;
        private String routingStatus;
        // สถานะ order ปัจจุบัน — ใช้บอกว่าจุดที่ข้ามในงานที่ปิดแล้ว กลับไปรอจัดส่ง หรือส่งไม่สำเร็จ
        private String orderStatus;
        // ข้อมูลสำหรับการ์ดจุดถัดไป — ปุ่ม "โทร", รายละเอียดสินค้า, เวลาส่ง ("ส่งแล้ว 09:10")
        private String tel;
        private String detail;
        private LocalDateTime deliveredAt;
    }
}
