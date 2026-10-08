package io.redlink.more.data.repository;

import io.redlink.more.data.model.ObservationResyncRequest;
import io.redlink.more.data.model.ResyncInterval;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@JdbcTest
@Import(ObservationResyncRepository.class)
class ObservationResyncRepositoryIT {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("more_test")
            .withUsername("more")
            .withPassword("more");

    @DynamicPropertySource
    static void registerDataSourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObservationResyncRepository repository;

    @BeforeEach
    void initSchema() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS observation_resync_requests");
        jdbcTemplate.execute("DROP TABLE IF EXISTS observations");
        jdbcTemplate.execute("CREATE TABLE observations (study_id BIGINT NOT NULL, observation_id INT NOT NULL, type VARCHAR NOT NULL, " +
                "PRIMARY KEY (study_id, observation_id))");
        // as created by the Studymanager migrations V1_27_0 and V1_28_0
        jdbcTemplate.execute("""
                CREATE TABLE observation_resync_requests (
                    study_id BIGINT NOT NULL,
                    participant_id INT NOT NULL,
                    observation_id INT NOT NULL,
                    created TIMESTAMP NOT NULL DEFAULT now(),
                    resync_interval TEXT NOT NULL DEFAULT 'urgent' CHECK (resync_interval IN ('urgent', 'high', 'normal', 'low')),
                    resync_start TIMESTAMPTZ NOT NULL DEFAULT now(),
                    resync_end TIMESTAMPTZ NOT NULL DEFAULT now(),
                    synced BOOLEAN NOT NULL DEFAULT FALSE,
                    PRIMARY KEY (study_id, participant_id, observation_id)
                )""");
        jdbcTemplate.update("INSERT INTO observations VALUES (1, 3, 'lime-survey-observation')");
    }

    @Test
    void upsertCreatesARunningRequestThatIsListedAsPending() {
        repository.upsert(1L, 2, 3, ResyncInterval.NORMAL, Duration.ofHours(24));

        List<ObservationResyncRequest> pending = repository.listPending();
        assertThat(pending).hasSize(1);
        ObservationResyncRequest request = pending.get(0);
        assertThat(request.observationType()).isEqualTo("lime-survey-observation");
        assertThat(request.interval()).isEqualTo(ResyncInterval.NORMAL);
        assertThat(Duration.between(request.start(), request.end())).isEqualTo(Duration.ofHours(24));
    }

    @Test
    void upsertMergesIntoARunningUrgentRequest() {
        // an operator request merged into a running portal request: urgent until the portal request ends
        repository.upsert(1L, 2, 3, ResyncInterval.NORMAL, Duration.ofHours(24));
        jdbcTemplate.update("UPDATE observation_resync_requests SET resync_interval = 'urgent'");
        repository.upsert(1L, 2, 3, ResyncInterval.NORMAL, Duration.ofHours(1));

        ObservationResyncRequest request = repository.listPending().get(0);
        assertThat(request.interval()).isEqualTo(ResyncInterval.URGENT);
        assertThat(Duration.between(request.start(), request.end())).isGreaterThan(Duration.ofHours(23));
    }

    @Test
    void upsertReplacesTheIntervalOfAnEndedRequest() {
        // an ended urgent request (end = start) does not make a portal request urgent
        jdbcTemplate.update("INSERT INTO observation_resync_requests (study_id, participant_id, observation_id) VALUES (1, 2, 3)");
        repository.upsert(1L, 2, 3, ResyncInterval.NORMAL, Duration.ofHours(24));

        assertThat(repository.listPending().get(0).interval()).isEqualTo(ResyncInterval.NORMAL);
    }

    @Test
    void markSyncedHidesTheRequestFromPendingAndUpsertRenewsIt() {
        repository.upsert(1L, 2, 3, ResyncInterval.NORMAL, Duration.ofHours(24));
        ObservationResyncRequest request = repository.listPending().get(0);

        assertThat(repository.markSynced(request)).isEqualTo(1);
        assertThat(repository.listPending()).isEmpty();

        repository.upsert(1L, 2, 3, ResyncInterval.NORMAL, Duration.ofHours(24));
        assertThat(repository.listPending()).hasSize(1);
    }

    @Test
    void aRenewedRequestIsNeitherDeletedNorMarkedSynced() {
        repository.upsert(1L, 2, 3, ResyncInterval.NORMAL, Duration.ofHours(24));
        ObservationResyncRequest outdated = repository.listPending().get(0);
        jdbcTemplate.update("UPDATE observation_resync_requests SET resync_start = resync_start + interval '1 minute'");

        assertThat(repository.markSynced(outdated)).isZero();
        assertThat(repository.delete(outdated)).isZero();

        assertThat(repository.delete(repository.listPending().get(0))).isEqualTo(1);
    }

    @Test
    void markSyncedByIdsCompletesAPendingRequest() {
        assertThat(repository.markSynced(1L, 2, 3)).isZero();

        repository.upsert(1L, 2, 3, ResyncInterval.NORMAL, Duration.ofHours(24));
        assertThat(repository.markSynced(1L, 2, 3)).isEqualTo(1);
        assertThat(repository.listPending()).isEmpty();
    }
}
