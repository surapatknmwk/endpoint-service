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
@Schema(description = "The caller's own profile")
public class ProfileResponse {

    @Schema(example = "driver1")
    private String username;

    @Schema(example = "ประยุทธ")
    private String firstName;

    @Schema(example = "ขับดี")
    private String lastName;

    @Schema(description = "10 digits without dashes; null = not set", example = "0891000001")
    private String phone;

    @Schema(description = "LINE profile picture URL; null when LINE is not linked")
    private String pictureUrl;

    @Schema(description = "Home address; null = no subdistrict set")
    private Address address;

    @Schema(description = "AVAILABLE, BUSY or OFF for a DRIVER account; null for an owner", example = "AVAILABLE")
    private String availability;

    @Schema(description = "DRIVER: has a started job (กำลังส่งงาน); always false for an owner")
    private boolean delivering;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Address {
        @Schema(description = "House number / street; may be null", example = "45 ถ.สุขเกษม")
        private String line;
        private String subdistrictCode;
        private String districtCode;
        private String provinceCode;
        private String subdistrictName;
        private String districtName;
        private String provinceName;
    }
}
