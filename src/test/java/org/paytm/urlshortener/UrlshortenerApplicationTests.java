package org.paytm.urlshortener;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.paytm.urlshortener.dto.ShortenResponse;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:urlshortener-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=validate"
})
class UrlshortenerApplicationTests {

    @Autowired
    private ObjectMapper objectMapper;

    @LocalServerPort
    private int port;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    @Test
    void contextLoads() {
    }

    @Test
    void shortenThenRedirectsToOriginalUrl() throws Exception {
        HttpResponse<String> shortened = postShorten(
                "{\"url\":\"https://Example.com/docs/../docs/readme?x=1\"}");
        ShortenResponse response = readShortenResponse(shortened);

        assertThat(shortened.statusCode()).isEqualTo(200);
        assertThat(response.code()).isNotBlank();
        assertThat(response.url()).isEqualTo("https://example.com/docs/readme?x=1");

        HttpResponse<String> redirect = get("/" + response.code());

        assertThat(redirect.statusCode()).isEqualTo(301);
        assertThat(redirect.headers().firstValue("Location")).hasValue("https://example.com/docs/readme?x=1");
    }

    @Test
    void unknownCodeReturns404() throws Exception {
        HttpResponse<String> response = get("/missing-code");

        assertThat(response.statusCode()).isEqualTo(404);
    }

    @Test
    void duplicateUrlReturnsExistingCode() throws Exception {
        String body = "{\"url\":\"https://example.com/same\"}";

        HttpResponse<String> first = postShorten(body);
        HttpResponse<String> second = postShorten(body);

        assertThat(second.statusCode()).isEqualTo(200);
        assertThat(readShortenResponse(second)).isEqualTo(readShortenResponse(first));
    }

    @Test
    void customAliasIsUsedForRedirect() throws Exception {
        HttpResponse<String> shortened = postShorten(
                "{\"url\":\"https://example.com/custom\",\"customAlias\":\"my_alias-1\"}");
        ShortenResponse response = readShortenResponse(shortened);

        assertThat(shortened.statusCode()).isEqualTo(200);
        assertThat(response.code()).isEqualTo("my_alias-1");

        HttpResponse<String> redirect = get("/my_alias-1");

        assertThat(redirect.statusCode()).isEqualTo(301);
        assertThat(redirect.headers().firstValue("Location")).hasValue("https://example.com/custom");
    }

    @Test
    void customAliasConflictReturns409() throws Exception {
        HttpResponse<String> first = postShorten(
                "{\"url\":\"https://example.com/one\",\"customAlias\":\"taken\"}");
        HttpResponse<String> second = postShorten(
                "{\"url\":\"https://example.com/two\",\"customAlias\":\"taken\"}");

        assertThat(first.statusCode()).isEqualTo(200);
        assertThat(second.statusCode()).isEqualTo(409);
    }

    @Test
    void invalidUrlReturns400() throws Exception {
        HttpResponse<String> response = postShorten("{\"url\":\"ftp://example.com/file\"}");

        assertThat(response.statusCode()).isEqualTo(400);
    }

    @Test
    void openApiDocsAreAvailable() throws Exception {
        HttpResponse<String> response = get("/api-docs");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"openapi\"");
        assertThat(response.body()).contains("\"/shorten\"");
    }

    private HttpResponse<String> postShorten(String json) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(localUrl("/shorten")))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(localUrl(path)))
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private ShortenResponse readShortenResponse(HttpResponse<String> response) throws Exception {
        return objectMapper.readValue(response.body(), ShortenResponse.class);
    }

    private String localUrl(String path) {
        return "http://localhost:" + port + path;
    }
}
