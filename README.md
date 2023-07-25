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


[SM-Backend]: https://github.com/MORE-Platform/more-studymanager-backend

[SM-Backend-Setup]: https://github.com/MORE-Platform/more-studymanager-backend#development-setup
