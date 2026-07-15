package org.paytm.urlshortener.repository;

import java.util.Optional;

import org.paytm.urlshortener.model.UrlMapping;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UrlMappingRepository extends JpaRepository<UrlMapping, Long> {
    Optional<UrlMapping> findByCode(String code);

    Optional<UrlMapping> findByOriginalUrl(String originalUrl);
}
