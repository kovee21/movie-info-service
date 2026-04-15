# Movie Info Service

REST API that aggregates movie metadata from two public sources — [OMDB](https://www.omdbapi.com/) and [TMDB](https://www.themoviedb.org/documentation/api) — behind a single endpoint, with Redis-backed response caching and MySQL-backed search auditing.

Built as a Java developer test exercise.

---

## Stack

| | |
|---|---|
| Platform | Spring Boot 4.0.5, Java 21 |
| Build / test | Maven, JUnit 5, Mockito, Testcontainers |
| Cache | Redis (`spring-data-redis`) |
| Database | MySQL 8.4 + Hibernate / JPA, Flyway migrations |
| Resilience | Resilience4j (retry + circuit breaker) |
| Docs | Springdoc OpenAPI 3 (`/v3/api-docs`, `/swagger-ui.html`) |
| Code style | Spotless + Google Java Format (AOSP), Lombok |

---

## API

### `GET /movies/{movieTitle}?api={omdb|tmdb}`

Returns matching movies (title, year, directors) from the selected upstream provider.

```
curl 'http://localhost:8080/movies/Avengers?api=omdb'
```

```json
{
  "movies": [
    { "Title": "The Avengers", "Year": "2012", "Director": ["Joss Whedon"] },
    { "Title": "Avengers: Endgame", "Year": "2019", "Director": ["Anthony Russo", "Joe Russo"] }
  ]
}
```

Error responses use a consistent shape: `{ "error": "..." }` — 400 for bad input, 502 for upstream failures, 503 when the circuit breaker is open, 500 for unexpected errors.

Full OpenAPI spec: [`docs/openapi.yml`](docs/openapi.yml) (regenerated on demand via `mvn test -Dopenapi.write=true`).

---

## Quick start

### Prerequisites
- Docker + Docker Compose
- JDK 21
- OMDB API key ([free signup](https://www.omdbapi.com/apikey.aspx))
- TMDB v4 Read Access Token ([signup](https://www.themoviedb.org/settings/api))

### Local run

```bash
# 1. Start MySQL + Redis (ports 3306 / 6379 by default; edit docker-compose.yml if taken)
docker compose up -d mysql redis

# 2. Run the app, injecting credentials
OMDB_API_KEY=xxx \
TMDB_BEARER_TOKEN=yyy \
mvn spring-boot:run

# 3. Hit the endpoint
curl 'http://localhost:8080/movies/Matrix?api=tmdb'
```

### Fully containerised (app + deps)

```bash
OMDB_API_KEY=xxx TMDB_BEARER_TOKEN=yyy docker compose up
```

---

## Architecture highlights

- **Strategy pattern** for providers — `MovieApiProvider` interface, one implementation per upstream. Adding a third source is one new class.
- **Redis caching** on the service layer (`@Cacheable`, 10-minute TTL). Cache key is `api:title` lowercased via `Locale.ROOT` (case-insensitive, safe for non-ASCII / Turkish `I`). Partial upstream failures bypass the cache so the next request can recover.
- **Fan-out detail fetching** — after the upstream search, per-movie detail calls run in parallel on a virtual-thread executor (`CompletableFuture.supplyAsync`), capped by `api.{provider}.max-detail-requests`.
- **Resilience4j** — retry (3 attempts, 200 ms exponential backoff) wrapped in a circuit breaker (10-call window, 50% failure or slow-call rate threshold, 30 s open duration). Configured programmatically in `ResilienceConfig`.
- **Async search audit** — every request (including cache hits) fires an `@Async` log write on a bounded `ThreadPoolTaskExecutor`. Back-pressure via `CallerRunsPolicy`.
- **Unicode-safe end-to-end** — `utf8mb4` column/charset, explicit JDBC UTF-8 params, `UriComponentsBuilder.encode()` on outbound calls.
- **API-key redaction** — a dedicated utility strips `apikey=...` / `api_key=...` from any string before logging. Ensures leaking stack traces cannot print keys.

---

## Configuration

All tunables live in `src/main/resources/application.yml` under `app.*` / `api.*`:

| Property | Default | Notes |
|---|---|---|
| `app.http.connect-timeout` | `5s` | applies to all outbound HTTP |
| `app.http.read-timeout` | `10s` | |
| `app.cache.ttl` | `10m` | Redis cache TTL |
| `app.async.search-log.*` | `4 / 8 / 2000` | core / max / queue |
| `api.omdb.key` | _required via env_ | `OMDB_API_KEY` |
| `api.tmdb.bearer-token` | _required via env_ | `TMDB_BEARER_TOKEN` |
| `api.{provider}.max-detail-requests` | `10` | fan-out cap |
| `api.tmdb.include-adult` | `true` | |

MySQL and Redis connection details come from `SPRING_DATASOURCE_URL` / `SPRING_DATA_REDIS_HOST` / `SPRING_DATA_REDIS_PORT` env vars (with sensible local defaults).

---

## Testing

```bash
mvn verify          # runs all tests + spotless:check
mvn test            # tests only
mvn spotless:apply  # auto-format code
```

**56 tests** across:
- **Unit** — providers (via `MockRestServiceServer`), service, utility
- **Slice** — `@WebMvcTest` for controller + global exception handler
- **Integration (H2 + simple cache)** — full request flow with spy-based providers
- **Full-stack (Testcontainers)** — real MySQL + real Redis, exercises Flyway migration, schema validation, JSON serialization round-trip, Unicode storage, and cache-key behaviour
- **Resilience** — retry + circuit breaker opening, short-circuiting, recovery

---

## Observability

- `/actuator/health` — DB, Redis, and circuit-breaker health aggregate
- `/actuator/info`
- `/actuator/metrics` — includes `http.server.requests.*`, `cache.gets{result=hit|miss}`, `resilience4j.circuitbreaker.state{name=omdb|tmdb}`

Log format is custom (`yyyy-MM-dd HH:mm:ss.SSS LVL [thread] logger : msg`). Business-level INFO logs cover every request with timing; cache hits produce one log line, cache misses two (controller + service).
