package org.paytm.urlshortener.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.regex.Pattern;

import org.paytm.urlshortener.model.UrlMapping;
import org.paytm.urlshortener.repository.UrlMappingRepository;
import org.paytm.urlshortener.util.Base62CodeGenerator;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UrlShortenerService {
    private static final Pattern CUSTOM_ALIAS = Pattern.compile("[A-Za-z0-9_-]{3,64}");

    private final UrlMappingRepository repository;
    private final Base62CodeGenerator codeGenerator;

    public UrlShortenerService(UrlMappingRepository repository, Base62CodeGenerator codeGenerator) {
        this.repository = repository;
        this.codeGenerator = codeGenerator;
    }

    @Transactional
    public UrlMapping shorten(String rawUrl, String rawCustomAlias) {
        if (rawUrl == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "url is required");
        }

        String normalizedUrl = normalizeUrl(rawUrl);
        String customAlias = normalizeAlias(rawCustomAlias);

        if (customAlias != null) {
            return createCustomAlias(normalizedUrl, customAlias);
        }

        return repository.findByOriginalUrl(normalizedUrl)
                .orElseGet(() -> createGeneratedMapping(normalizedUrl));
    }

    @Transactional(readOnly = true)
    public UrlMapping findByCode(String code) {
        return repository.findByCode(code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown short code"));
    }

    private UrlMapping createCustomAlias(String normalizedUrl, String customAlias) {
        var existingAlias = repository.findByCode(customAlias);
        if (existingAlias.isPresent()) {
            UrlMapping existing = existingAlias.get();
            if (existing.getOriginalUrl().equals(normalizedUrl)) {
                return existing;
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Custom alias is already in use");
        }

        var existingUrl = repository.findByOriginalUrl(normalizedUrl);
        if (existingUrl.isPresent()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This URL already has short code " + existingUrl.get().getCode());
        }

        return saveMapping(new UrlMapping(customAlias, normalizedUrl));
    }

    private UrlMapping createGeneratedMapping(String normalizedUrl) {
        UrlMapping mapping = saveMapping(new UrlMapping(null, normalizedUrl));
        mapping.setCode(codeGenerator.encode(mapping.getId()));
        return saveMapping(mapping);
    }

    private UrlMapping saveMapping(UrlMapping mapping) {
        try {
            return repository.saveAndFlush(mapping);
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Short code or URL already exists", ex);
        }
    }

    private String normalizeUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "url is required");
        }

        try {
            URI uri = new URI(rawUrl.trim()).normalize();
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "URL must include scheme and host");
            }

            String lowerScheme = scheme.toLowerCase(Locale.ROOT);
            if (!lowerScheme.equals("http") && !lowerScheme.equals("https")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only http and https URLs are supported");
            }

            URI normalized = new URI(
                    lowerScheme,
                    uri.getUserInfo(),
                    host.toLowerCase(Locale.ROOT),
                    uri.getPort(),
                    uri.getPath(),
                    uri.getQuery(),
                    uri.getFragment());
            return normalized.toASCIIString();
        } catch (URISyntaxException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid URL", ex);
        }
    }

    private String normalizeAlias(String rawCustomAlias) {
        if (rawCustomAlias == null || rawCustomAlias.isBlank()) {
            return null;
        }

        String alias = rawCustomAlias.trim();
        if (alias.equals("shorten")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Alias is reserved");
        }
        if (alias.startsWith(Base62CodeGenerator.GENERATED_PREFIX)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Alias prefix is reserved for generated codes");
        }
        if (!CUSTOM_ALIAS.matcher(alias).matches()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Alias must be 3-64 URL-safe characters: letters, numbers, underscores, or hyphens");
        }
        return alias;
    }
}
