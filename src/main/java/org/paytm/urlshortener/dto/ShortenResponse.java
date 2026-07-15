package org.paytm.urlshortener.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record ShortenResponse(
        @Schema(description = "Generated short code or custom alias", example = "u_1")
        String code,

        @Schema(description = "Normalized original URL", example = "https://example.com/docs")
        String url) {
}
