package com.authen.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RegisterConfigResponse {
    private boolean enabled;
    private boolean otpRequired;
    private String termsVersion;
    private boolean lineEnabled;
    /** Username + password log-in (feature flag FF_LOGIN). */
    private boolean loginEnabled;
}
