package org.paytm.urlshortener.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record ShortenRequest(
        @Schema(description = "Original HTTP or HTTPS URL to shorten", example = "https://example.com/docs")
        String url,

        @Schema(description = "Optional custom alias, 3-64 URL-safe characters", example = "my-link")
        String customAlias) {
}
