package com.core.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Body of skipping or cancelling a stop — reason required, note required for OTHER (com.core.constant.StopReason). */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Why the stop is skipped / cancelled")
public class StopActionRequest {

    @Schema(description = "Skip: CUSTOMER_ABSENT, NO_ANSWER, ADDRESS_UNCLEAR, ROAD_BLOCKED, CUSTOMER_POSTPONED, OTHER · "
            + "Cancel: CHANGED_MIND, DUPLICATE_ORDER, NOT_ORDERED, WRONG_ITEM, OTHER", example = "CUSTOMER_ABSENT")
    private String reason;

    @Size(max = 200)
    @Schema(description = "Free text; required with OTHER, optional otherwise", example = "ฝากเพื่อนบ้านไม่ได้")
    private String note;

    @Schema(description = "Optional phone position when the button was pressed")
    private GeoPoint location;

    public StopActionRequest(String reason, String note) {
        this.reason = reason;
        this.note = note;
    }
}
