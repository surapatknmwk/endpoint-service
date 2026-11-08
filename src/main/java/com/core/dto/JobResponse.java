package com.core.dto;

import com.core.dto.master.AddressInfoDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
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

    @Schema(description = "Username of the owner who created the job", example = "admin")
    private String ownerUsername;

    @Schema(description = "Owner's first + last name, or the username", example = "สมศักดิ์ เจ้าของสอง")
    private String ownerDisplayName;

    @Schema(description = "Owner's LINE profile picture URL; null when not linked")
    private String ownerPictureUrl;

    @Schema(description = "The driver who works the job; null = not assigned. phone only for the owner, same team")
    private TeamUserSummary assignedDriver;

    @Schema(description = "The owner delivers this job themselves (no driver, chosen as \"ฉันส่งเอง\")",
            example = "false")
    private boolean ownerDelivers;

    @Schema(description = "Owner's phone — only for the assigned driver while in the owner's team; null otherwise",
            example = "0812000002")
    private String ownerPhone;

    @Schema(description = "What the caller may do: MANAGE (owner — everything) or OPERATE (assigned driver — "
            + "start, stop actions, close)", example = "MANAGE")
    private String myAccess;

    @Schema(description = "The request waiting for a driver's answer — owner only; null when none (US-TEAM-05)")
    private DriverRequestInfo driverRequest;

    @Schema(description = "The newest request was declined and the job still has no driver — owner only; null "
            + "otherwise")
    private DriverRequestInfo lastDeclined;

    @Schema(description = "Drivers who declined this job at least once — owner only, for the pick-a-driver sheet")
    private List<Integer> declinedDriverIds;

    @Schema(description = "Ordered list of delivery stops")
    private List<RoutingItem> routings;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DriverRequestInfo {
        private Long requestId;
        private TeamUserSummary driver;
        private LocalDateTime requestedAt;
        // PENDING / DECLINED / EXPIRED (ไม่ตอบภายในเวลา) / RELEASED (คืนงาน)
        private String requestStatus;
        // รอตอบเท่านั้น: คำขอหมดอายุเมื่อไร (JobRequestPolicy)
        private LocalDateTime expiresAt;
        // ปฏิเสธ / คืนงาน: รหัสเหตุผล (null = ไม่ระบุ), ข้อความที่แสดง ("ไกลเกินไป" / ข้อความที่พิมพ์ของ "อื่นๆ"), เวลาที่ตอบ
        private String declineReason;
        private String declineReasonText;
        private LocalDateTime respondedAt;
    }

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
        // เหตุผลที่ข้าม / ยกเลิก / แจ้งยกเลิก (StopReason) และข้อความภาษาไทย — null เมื่อไม่มี
        private String statusReason;
        private String statusReasonText;
        // สถานะ order ปัจจุบัน — ใช้บอกว่าจุดที่ข้ามในงานที่ปิดแล้ว กลับไปรอจัดส่ง หรือส่งไม่สำเร็จ
        private String orderStatus;
        // ข้อมูลสำหรับการ์ดจุดถัดไป — ปุ่ม "โทร", รายละเอียดสินค้า, เวลาส่ง ("ส่งแล้ว 09:10")
        private String tel;
        private String detail;
        private LocalDateTime deliveredAt;
        // ราคาของ order — null เมื่อผู้ดูเป็นคนส่งและเจ้าของไม่ได้เปิดให้เห็น (ตัดสินที่ backend ไม่ใช่แค่ซ่อนในเว็บ)
        private BigDecimal price;
    }
}
