package com.core.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Contact details of the other person in a team — only while the team row is INVITED or ACTIVE")
public class ContactInfo {

    @Schema(description = "10 digits without dashes; null = not set", example = "0891000001")
    private String phone;

    @Schema(description = "Home address as one line; null = not set",
            example = "45 ถ.สุขเกษม ต.ธาตุเชิงชุม อ.เมืองสกลนคร จ.สกลนคร")
    private String addressText;
}
