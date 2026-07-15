package org.paytm.urlshortener.service;

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.paytm.urlshortener.model.UrlMapping;
import org.paytm.urlshortener.repository.UrlMappingRepository;
import org.paytm.urlshortener.util.Base62CodeGenerator;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UrlShortenerServiceTest {

    private UrlMappingRepository repository;
    private UrlShortenerService service;

    @BeforeEach
    void setUp() {
        repository = mock(UrlMappingRepository.class);
        service = new UrlShortenerService(repository, new Base62CodeGenerator());
    }

    @Test
    void createsGeneratedCodeFromDatabaseId() {
        stubSaveAndFlushAssigningIds();
        when(repository.findByOriginalUrl("https://example.com/a")).thenReturn(Optional.empty());

        UrlMapping mapping = service.shorten("https://example.com/a", null);

        assertThat(mapping.getId()).isEqualTo(1L);
        assertThat(mapping.getCode()).isEqualTo("u_1");
        assertThat(mapping.getOriginalUrl()).isEqualTo("https://example.com/a");
    }

    @Test
    void returnsExistingMappingWhenSameUrlIsShortenedAgainWithoutAlias() {
        UrlMapping existing = mapping(7L, "u_7", "https://example.com/same");
        when(repository.findByOriginalUrl("https://example.com/same")).thenReturn(Optional.of(existing));

        UrlMapping mapping = service.shorten("https://example.com/same", null);

        assertThat(mapping).isSameAs(existing);
    }

    @Test
    void createsCustomAliasWhenAvailable() {
        stubSaveAndFlushAssigningIds();
        when(repository.findByCode("my-link")).thenReturn(Optional.empty());
        when(repository.findByOriginalUrl("https://example.com/custom")).thenReturn(Optional.empty());

        UrlMapping mapping = service.shorten("https://example.com/custom", " my-link ");

        assertThat(mapping.getCode()).isEqualTo("my-link");
        assertThat(mapping.getOriginalUrl()).isEqualTo("https://example.com/custom");
    }

    @Test
    void returnsExistingAliasWhenAliasAlreadyPointsToSameUrl() {
        UrlMapping existing = mapping(4L, "docs", "https://example.com/docs");
        when(repository.findByCode("docs")).thenReturn(Optional.of(existing));

        UrlMapping mapping = service.shorten("https://example.com/docs", "docs");

        assertThat(mapping).isSameAs(existing);
    }

    @Test
    void rejectsCustomAliasAlreadyUsedForAnotherUrl() {
        when(repository.findByCode("taken"))
                .thenReturn(Optional.of(mapping(2L, "taken", "https://example.com/one")));

        assertThatThrownBy(() -> service.shorten("https://example.com/two", "taken"))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void rejectsReservedAliases() {
        assertThatThrownBy(() -> service.shorten("https://example.com/a", "shorten"))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> service.shorten("https://example.com/a", "u_custom"))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void rejectsInvalidUrls() {
        assertThatThrownBy(() -> service.shorten("ftp://example.com/file", null))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> service.shorten("example.com/no-scheme", null))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void unknownCodeReturnsNotFound() {
        when(repository.findByCode("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findByCode("missing"))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void dataIntegrityViolationReturnsConflict() {
        when(repository.findByOriginalUrl("https://example.com/race")).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(UrlMapping.class))).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> service.shorten("https://example.com/race", null))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void findByCodeReturnsMapping() {
        UrlMapping existing = mapping(3L, "u_3", "https://example.com/found");
        when(repository.findByCode("u_3")).thenReturn(Optional.of(existing));

        assertThat(service.findByCode("u_3")).isSameAs(existing);
        verify(repository).findByCode("u_3");
    }

    private void stubSaveAndFlushAssigningIds() {
        AtomicLong ids = new AtomicLong(1);
        when(repository.saveAndFlush(any(UrlMapping.class))).thenAnswer(invocation -> {
            UrlMapping mapping = invocation.getArgument(0);
            if (mapping.getId() == null) {
                setId(mapping, ids.getAndIncrement());
            }
            return mapping;
        });
    }

    private UrlMapping mapping(Long id, String code, String originalUrl) {
        UrlMapping mapping = new UrlMapping(code, originalUrl);
        setId(mapping, id);
        return mapping;
    }

    private void setId(UrlMapping mapping, Long id) {
        try {
            Field idField = UrlMapping.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(mapping, id);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Could not set test id", ex);
        }
    }
}
