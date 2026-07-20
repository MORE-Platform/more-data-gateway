# MORE Data Gateway

* [Architecture Decision Records](docs/adr)

## Development Setup

The Data Gateway is tightly coupled to [Studymanager Backend][SM-Backend]. For local development
there are two modes: _Combined_ and _Standalone_, where the first is recommended.

### Combined Setup

For the combined setup, first run the [development setup for the Studymanager Backend][SM-Backend-Setup].
Make sure to start the Studymanager Backend at least once, as this will initialize the database and all other required
services.

Keep the `docker-compose.yaml` of the Studymanager running, then you can start the Data Gateway locally. The default
settings in the `application.yaml` are prepared to work with these services.

### Standalone Setup

The repository contains a `docker-compose.yaml` that can be used to launch the required services
for local development:

```shell
docker compose up -d
```

After that, you need to start the Data Gateway using the `standalone` spring-profile to initialize the database.

The default settings in the `application.yaml` are set to use these local services. Please note that these services bind
to the same ports as those for the Studymanager Backend, so running both at the same time will lead to conflicts.

## Deployment & Tagging Strategy

This repository uses Git tags to manage releases and Docker image deployments.

### Tag Format

Tags should follow the semantic versioning format: `v<Major>.<Minor>.<Patch>`
Example: `v1.0.1`

### Automated Deployment

- **Branch Pushes**: Pushes to `main`, `develop` branches automatically trigger a Docker image
  build and push to the GitHub Container Registry (GHCR), tagged with the branch name.
- **Pull Requests**: The "Test and Compile" workflow runs on all pull requests to ensure code quality.
- **Tag Pushes**: Pushing a tag matching the `v*.*.*` pattern triggers a build and push to GHCR. The image will be
  tagged with the version (e.g., `1.0.1`) and the full tag (e.g., `v1.0.1`).
- **Manual**: Workflows can be triggered manually via `workflow_dispatch` with an optional custom Docker tag.

## Configuration

The Data Gateway can be configured using environment variables. The following table lists the most important
configuration
properties:

| Property                          | Environment Variable                                | Default Value                           | Description                                                                                                        |
|-----------------------------------|-----------------------------------------------------|-----------------------------------------|--------------------------------------------------------------------------------------------------------------------|
| `more.gateway.baseUrl`            | `BASE_URL`                                          | -                                       | The base URL of the Data Gateway.                                                                                  |
| `more.login-token.hash-algorithm` | `LOGIN_TOKEN_HASH_ALGORITHM`                        | `SHA-256`                               | The hash algorithm used to store and verify login tokens. Must be a valid `java.security.MessageDigest` algorithm. |
| `elastic.host`                    | `ELASTIC_HOST`                                      | `localhost`                             | The host of the Elasticsearch instance.                                                                            |
| `elastic.port`                    | `ELASTIC_PORT`                                      | `9200`                                  | The port of the Elasticsearch instance.                                                                            |
| `spring.datasource.url`           | `POSTGRES_HOST`, `POSTGRES_PORT`, `POSTGRES_DBNAME` | `jdbc:postgresql://localhost:5432/more` | The JDBC URL of the PostgreSQL database.                                                                           |

[SM-Backend]: https://github.com/MORE-Platform/more-studymanager-backend

[SM-Backend-Setup]: https://github.com/MORE-Platform/more-studymanager-backend#development-setup
