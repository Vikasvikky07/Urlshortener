package org.paytm.urlshortener.controller;

import java.net.URI;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.paytm.urlshortener.dto.ShortenRequest;
import org.paytm.urlshortener.dto.ShortenResponse;
import org.paytm.urlshortener.model.UrlMapping;
import org.paytm.urlshortener.service.UrlShortenerService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "URL Shortener", description = "Create short links and redirect by short code")
public class UrlShortenerController {
    private final UrlShortenerService service;

    public UrlShortenerController(UrlShortenerService service) {
        this.service = service;
    }

    @PostMapping("/shorten")
    @Operation(
            summary = "Create a short URL",
            description = "Creates a generated short code or uses the provided custom alias.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Short URL created or existing short URL returned",
                            content = @Content(schema = @Schema(implementation = ShortenResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid URL or alias", content = @Content),
                    @ApiResponse(responseCode = "409", description = "URL or alias already exists", content = @Content)
            })
    public ShortenResponse shorten(@RequestBody ShortenRequest request) {
        if (request == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "request body is required");
        }
        UrlMapping mapping = service.shorten(request.url(), request.customAlias());
        return new ShortenResponse(mapping.getCode(), mapping.getOriginalUrl());
    }

    @GetMapping("/{code}")
    @Operation(
            summary = "Redirect to the original URL",
            description = "Redirects a short code to the original URL using HTTP 301.",
            responses = {
                    @ApiResponse(responseCode = "301", description = "Redirect to original URL", content = @Content),
                    @ApiResponse(responseCode = "404", description = "Short code not found", content = @Content)
            })
    public ResponseEntity<Void> redirect(
            @Parameter(description = "Short code or custom alias", example = "u_1")
            @PathVariable String code) {
        UrlMapping mapping = service.findByCode(code);
        return ResponseEntity.status(HttpStatus.MOVED_PERMANENTLY)
                .header(HttpHeaders.LOCATION, URI.create(mapping.getOriginalUrl()).toASCIIString())
                .build();
    }
}
