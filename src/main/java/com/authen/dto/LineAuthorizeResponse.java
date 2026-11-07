package com.authen.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LineAuthorizeResponse {
    private String authorizeUrl;
}
