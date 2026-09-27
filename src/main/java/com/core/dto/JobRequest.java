package com.core.dto;

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
@Schema(description = "Job creation request payload")
public class JobRequest {

    @Schema(description = "Job name", example = "รอบส่งเช้า อำเภอ A - อำเภอ B")
    private String jobName;

    @Schema(description = "Origin (starting point) subdistrict code", example = "470101")
    private String originSubdistrictCode;

    @Schema(description = "Scheduled delivery date")
    private LocalDateTime scheduledDate;

    @Schema(description = "Order IDs selected for this job, in delivery stop order")
    private List<Long> orderIds;
}
