package com.master.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Expanded Google Maps link")
public record MapLinkResponse(
        @Schema(description = "Full Google Maps URL the short link redirects to",
                example = "https://www.google.com/maps/place/.../@17.1545,104.1348,17z/data=!3d17.1601!4d104.1402")
        String url) {
}
