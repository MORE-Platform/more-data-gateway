# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Do not commit. Do not use a function or class, that does not exist. Always test the code. Always reuse what already
exists. Keep it simple. Never commit any changes to git.

## What this is

MORE Data Gateway: a Spring Boot 3 (Java 17) service that ingests observation/health data from mobile apps and
third-party integrations (Garmin, LimeSurvey), stores it in Elasticsearch as time-series data, and manages
study/participant metadata (goals, schedules, notifications) in PostgreSQL. It is tightly coupled to, and normally run
alongside, the
[Studymanager Backend](https://github.com/MORE-Platform/more-studymanager-backend).

## Development Setup

Two local modes — see README.md for details:

- **Combined** (recommended): run the Studymanager Backend's dev setup first (it initializes DB and services), keep its
  `docker-compose.yaml` running, then start this app with default settings.
- **Standalone**: `docker compose up -d` in this repo (starts Postgres, Redis, Elasticsearch, Kibana), then run the app
  with the `standalone` Spring profile. Note: standalone services bind the same ports as the Studymanager's, so don't
  run both simultaneously.

## Build, Test, Run

```shell
./mvnw compile test          # compile + run unit/integration tests (what CI runs)
./mvnw -Dtest=ClassName test  # run a single test class
./mvnw -Dtest=ClassName#methodName test  # run a single test method
./mvnw spring-boot:run -Dspring-boot.run.profiles=standalone
```

- Tests are JUnit 5. Files ending `*Test.java` are plain unit tests; files ending `*IT.java`
  (e.g. `ParticipantKeyValueRepositoryIT`) are Testcontainers-backed integration tests that spin up a real
  Postgres/Elasticsearch container — Docker must be running for those.
- CI (`.github/workflows/compile-test.yml`) runs `./mvnw compile test` on every PR, and on pushes to
  `main`/`develop` additionally builds and pushes a JIB-built Docker image to GHCR. Tags matching
  `v*.*.*` trigger a versioned release image.

## Architecture

### API-first via generated code

All REST surfaces are defined as OpenAPI specs under `src/main/resources/openapi/*.yaml` (e.g.
`MobileAppAPI.yaml`, `GoalAPI.yaml`, `ObservationExecutionAPI.yaml`, `ParticipantPortalAPI.yaml`,
`GarminAPI.yaml`, `CustomModelAPI.yaml`). The `openapi-generator-maven-plugin` (configured in
`pom.xml`) generates two kinds of code at build time into `target/generated-sources/*`:

1. **Server interfaces + DTOs** (spring generator, `interfaceOnly=true`) for this app's own APIs — controllers in
   `src/main/java/.../controller/` implement the generated `*Api` interfaces (e.g.
   `DataApiV1Controller implements DataApi`). To change a request/response shape, edit the YAML spec, not generated
   code.
2. **REST client SDKs** (`resttemplate` library) for outbound calls to external systems: Garmin Wellness API, LimeSurvey
   Remote Control API, the Custom Model API, and the Health Transformation API. Client code lives under `io.redlink.more.data.{garmin.wellness,limesurvey,
   custom,health}` packages after generation.

`TimeSeriesAPI.yaml` at the repo root only documents the Elasticsearch datapoint schema; it does not generate code.

### Data layer

- **PostgreSQL** holds relational/study metadata (studies, participants, goals, schedules, notifications, login tokens).
  There is no ORM/Spring Data repository abstraction in use despite the `spring-boot-starter-data-jdbc` dependency —
  repositories under
  `src/main/java/.../repository/` are plain `@Component` classes using `JdbcTemplate` /
  `NamedParameterJdbcTemplate` with hand-written SQL (see `GoalRepository`, `StudyRepository`).
- **Schema migrations** use Flyway, but the whole schema currently lives in a single squashed file
  `src/main/resources/db/migration/V1_0_0__init.sql` rather than incremental version files — check git history/team
  convention before deciding whether to edit that file in place or add a new
  `V1_x_x__*.sql` migration.
- **Elasticsearch** is the time-series store for observation datapoints, accessed via
  `ElasticService` (implements `StorageService`, see `io.redlink.more.data.api`). Elasticsearch's own Spring Boot
  auto-configuration is explicitly excluded in `MoreDataGatewayApplication`
  (`ElasticsearchRestClientAutoConfiguration`) in favor of custom config in
  `configuration/ElasticConfiguration.java`.
- **Redis** backs Spring Session (`spring-session-data-redis`) for session storage.

### Domain areas

- `service/observations/` — observation execution, including a LimeSurvey integration (`observations/limesurvey/`) that
  talks to LimeSurvey's Remote Control API.
- `service/garmin/` + `transformers/garmin/` — Garmin Connect integration: token refresh (scheduled via
  `component/SchedulingComponent`, a `TaskScheduler` job driven by
  `GarminProperties.tokenRefresh()` cron config), and per-metric transformers (heart rate, sleep, steps, blood pressure,
  activity) that convert Garmin's wellness payloads into the internal datapoint model before storage.
- `service/goal/` + `model/goal/` + `repository/GoalRepository` — goal templates, per-study goal configuration,
  adherence checks, tied to observation groups/topics.
- `model/scheduler/` — the scheduling domain model (recurrence rules, intervals, randomization, relative/absolute
  events) used to compute when observations/milestones are due; related helpers live in `util/SchedulerUtils.java`,
  `util/RandomSchedulerUtils.java`, `util/DateTimeUtils.java`.
- `controller/` — one controller per generated API interface, plus
  `GlobalControllerExceptionHandler` for centralized error mapping and
  `controller/transformer/` for request/response shaping.

### Security

- HTTP Basic auth (`configuration/SecurityConfig`), backed by `GatewayUserDetailService`, with a
  `DelegatingPasswordEncoder` supporting bcrypt/pbkdf2/scrypt/argon2/noop by prefix.
- Authorization is coarse path-based rule matching in `SecurityConfig#filterChain`: several paths are explicitly
  `permitAll()` (registration, signup, login, participant portal login, external bulk ingest, calendar `.ics` export,
  Garmin webhook callbacks), everything else under
  `/api/v1/**`, `/goals/api/v1/**`, `/participant-portal/api/v1/**` requires authentication, and
  `anyRequest().denyAll()` is the default-deny fallback. New endpoints must be added to this list explicitly or they
  will be denied.
- `AuthenticationFacade` + `GatewayUserDetails` carry per-request routing/authority info (e.g.
  `GatewayUserDetailService.APP_ROLE`) that controllers check via `assertAuthority(...)`.
- Firewall-rejected requests return HTTP 418 (`I_AM_A_TEAPOT`) by design (`requestRejectedHandler`), used to distinguish
  blocked requests from normal 4xx responses.

### Configuration

Runtime config is environment-variable driven — see the table in README.md for the key ones (`BASE_URL`,
`LOGIN_TOKEN_HASH_ALGORITHM`, `ELASTIC_HOST`/`ELASTIC_PORT`, `POSTGRES_*`).
`@ConfigurationPropertiesScan` is enabled app-wide, so most `*Properties` records/classes under
`configuration/` and `properties/` are picked up automatically without manual `@Bean` registration.
