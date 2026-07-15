# URL Shortener Write-Up

## 1. What I Asked AI To Do vs. What I Decided Myself

I used AI as a coding assistant to help scaffold and refine the URL shortener service. I asked it to help organize the Spring Boot project into controller, service, repository, model, DTO, and utility packages; add Flyway migrations; add Swagger/OpenAPI documentation; improve the README; and add automated tests for the core logic and edge cases.

The main product decisions were mine: use Spring Boot with Java, persist mappings in PostgreSQL, support both generated short codes and custom aliases, return `301` redirects, return `404` for unknown codes, and make duplicate shortening of the same normalized URL idempotent when no custom alias is provided. I also decided to use database-generated IDs as the source for generated short codes because that gives a simple uniqueness guarantee.

## 2. Where I Overrode or Corrected AI Output

I corrected the datastore direction during development. At one point I asked to switch from PostgreSQL to SQLite, but then decided to revert back to PostgreSQL because it better matches a production-style service and works cleanly with Flyway, JPA, and Docker Compose for local development.

I also asked the AI to restructure the project into separate packages after the initial implementation placed most classes in one package. That improved readability and made the code easier to explain in a follow-up session.

I kept Hibernate schema generation disabled for runtime and used Flyway instead. The AI initially helped wire schema behavior, but I chose to keep `spring.jpa.hibernate.ddl-auto=validate` so database changes remain explicit through migrations.

## 3. Biggest Trade-Offs and Alternatives

The first trade-off was the short-code generation strategy. I used the database-generated primary key, encoded in Base62 with a reserved `u_` prefix. This is simple, deterministic, and collision-free because database IDs are unique and custom aliases cannot start with `u_`. The downside is that codes reveal relative creation order. An alternative would be random codes with retry-on-conflict, which hides ordering but requires collision handling and more complexity.

The second trade-off was duplicate URL behavior. I made shortening the same normalized URL idempotent: calling `POST /shorten` twice with the same URL returns the existing code. This avoids duplicate records and makes the API predictable. The alternative would be to create a new short code every time, which may be useful for analytics or per-user tracking, but that was outside the scope.

The third trade-off was validation strictness. I only allow absolute `http` and `https` URLs with a host. This rejects schemes like `ftp` and relative URLs. That keeps redirect behavior safer and clearer, though a more flexible system might support additional schemes or more advanced URL validation.

## 4. What’s Missing or What I’d Do With Another Day

With another day, I would add rate limiting, structured error response bodies, and stronger validation against unsafe redirect targets such as private-network URLs if this were internet-facing. I would also add request/response DTO validation annotations, controller-level tests with mocked services, and possibly Testcontainers for PostgreSQL integration tests instead of using H2 in tests.

I would also add observability basics such as request logging, metrics for created links and redirects, and maybe a redirect count field. Finally, I would consider adding expiration support, per-user ownership, and admin/listing endpoints depending on the product requirements.
