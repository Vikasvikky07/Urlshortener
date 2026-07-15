# URL Shortener

Spring Boot URL shortener with generated short codes, custom aliases, PostgreSQL persistence, Flyway migrations, and Swagger/OpenAPI documentation.

## Requirements

- Java 17
- Docker Desktop, only needed if you want to run PostgreSQL locally with Docker Compose

## Install

Clone the project and install dependencies through Gradle:

```bash
./gradlew build
```

On Windows PowerShell:

```powershell
.\gradlew.bat build
```

## Database

The app uses PostgreSQL by default:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/urlshortener
spring.datasource.username=postgres
spring.datasource.password=9908
```

Start a local PostgreSQL container:

```bash
docker compose up -d postgres
```

Override connection settings with environment variables if needed:

```bash
export DATABASE_URL=jdbc:postgresql://localhost:5432/urlshortener
export DATABASE_USERNAME=postgres
export DATABASE_PASSWORD=9908
```

Flyway creates the `url_mappings` table from `src/main/resources/db/migration`.

## Run

```bash
./gradlew bootRun
```

On Windows PowerShell:

```powershell
.\gradlew.bat bootRun
```

The service starts on:

```text
http://localhost:8080
```

Swagger UI:

```text
http://localhost:8080/swagger-ui.html
```

OpenAPI JSON:

```text
http://localhost:8080/api-docs
```

## API

Create a generated short code:

```bash
curl -i -X POST http://localhost:8080/shorten \
  -H "Content-Type: application/json" \
  -d "{\"url\":\"https://example.com/a/long/path\"}"
```

Response:

```json
{
  "code": "u_1",
  "url": "https://example.com/a/long/path"
}
```

Create a custom alias:

```bash
curl -i -X POST http://localhost:8080/shorten \
  -H "Content-Type: application/json" \
  -d "{\"url\":\"https://example.com/docs\",\"customAlias\":\"my-docs\"}"
```

Redirect by short code or alias:

```bash
curl -i http://localhost:8080/my-docs
```

Expected result: `301 Moved Permanently` with the `Location` header set to the original URL.

## Endpoint Checks

Unknown code returns `404`:

```bash
curl -i http://localhost:8080/missing-code
```

Invalid URL returns `400`:

```bash
curl -i -X POST http://localhost:8080/shorten \
  -H "Content-Type: application/json" \
  -d "{\"url\":\"ftp://example.com/file\"}"
```

Duplicate URL without a custom alias is idempotent and returns the existing code:

```bash
curl -i -X POST http://localhost:8080/shorten \
  -H "Content-Type: application/json" \
  -d "{\"url\":\"https://example.com/same\"}"

curl -i -X POST http://localhost:8080/shorten \
  -H "Content-Type: application/json" \
  -d "{\"url\":\"https://example.com/same\"}"
```

Alias conflict returns `409`:

```bash
curl -i -X POST http://localhost:8080/shorten \
  -H "Content-Type: application/json" \
  -d "{\"url\":\"https://example.com/one\",\"customAlias\":\"taken\"}"

curl -i -X POST http://localhost:8080/shorten \
  -H "Content-Type: application/json" \
  -d "{\"url\":\"https://example.com/two\",\"customAlias\":\"taken\"}"
```

Reserved alias returns `400`:

```bash
curl -i -X POST http://localhost:8080/shorten \
  -H "Content-Type: application/json" \
  -d "{\"url\":\"https://example.com/reserved\",\"customAlias\":\"shorten\"}"
```

## Test

Run all automated tests:

```bash
./gradlew test
```

On Windows PowerShell:

```powershell
.\gradlew.bat test
```

The tests cover:

- `POST /shorten`
- `GET /{code}` redirect behavior
- Unknown code `404`
- Invalid URL `400`
- Custom aliases
- Alias conflict `409`
- Duplicate URL idempotency
- OpenAPI docs availability
- Base62 code generation
- Service-level edge cases

## Design Decisions

- Mappings are persisted in PostgreSQL.
- Database schema is managed by Flyway, while Hibernate validates the schema with `ddl-auto=validate`.
- URLs must be absolute `http` or `https` URLs with a host. Scheme and host are normalized to lowercase, and the URI path is normalized.
- Shortening the same normalized URL twice without a custom alias is idempotent: the existing code is returned.
- Custom aliases must be 3-64 URL-safe characters: letters, numbers, underscores, or hyphens. `shorten` and the generated-code prefix `u_` are reserved.
- A URL may have only one short code. If a custom alias is requested for a URL that already has a different code, the service returns `409 Conflict`.
- Generated codes are `u_` plus a Base62 encoding of the database-generated primary key. The database assigns each id once, and custom aliases cannot use the `u_` prefix, so generated codes cannot collide with generated codes or aliases.
