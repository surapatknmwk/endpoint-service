package com.authen.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

/** {@code result} = LOGGED_IN (token) · REGISTER (ticket, LINE name) · LINKED (line). */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LineCallbackResponse {
    public static final String LOGGED_IN = "LOGGED_IN";
    public static final String REGISTER = "REGISTER";
    public static final String LINKED = "LINKED";

    private String result;
    private String token;
    private String registrationTicket;
    private Long expiresInSeconds;
    private String lineDisplayName;
    private LineLinkResponse line;
}
