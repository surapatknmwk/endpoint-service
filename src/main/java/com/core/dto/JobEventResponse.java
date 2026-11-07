package com.core.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** One line of a job's history — shown to the owner and the assigned driver alike. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "One action on a job: who, what, when")
public class JobEventResponse {

    @Schema(description = "Event ID", example = "31")
    private Long eventId;

    @Schema(description = "com.core.constant.JobEventType", example = "STOP_SKIPPED")
    private String eventType;

    @Schema(description = "Order of the stop; null for a job-level action", example = "105")
    private Long orderId;

    @Schema(description = "Customer of the stop; null for a job-level action", example = "คุณสมใจ")
    private String customerName;

    @Schema(description = "Reason code (StopReason); null when none", example = "CUSTOMER_ABSENT")
    private String reason;

    @Schema(description = "Reason as Thai text, with the typed note", example = "ลูกค้าไม่อยู่")
    private String reasonText;

    @Schema(description = "Who did it")
    private TeamUserSummary actor;

    @Schema(description = "true = the job's owner did it, false = the driver")
    private boolean byOwner;

    @Schema(description = "When (server time, Asia/Bangkok)")
    private LocalDateTime createdAt;

    @Schema(description = "Phone position when pressed; null = not sent (refused / no GPS / not a stop action)")
    private BigDecimal lat;

    private BigDecimal lng;

    @Schema(description = "GPS accuracy radius, metres", example = "25")
    private Integer accuracyM;

    @Schema(description = "Straight line from that position to the centre of the customer's subdistrict, km",
            example = "0.84")
    private BigDecimal distanceKm;
}
