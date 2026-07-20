package io.redlink.more.data.repository;

import io.redlink.more.data.model.ParticipantKeyValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@JdbcTest
@Import(ParticipantKeyValueRepository.class)
@TestMethodOrder(OrderAnnotation.class)
class ParticipantKeyValueRepositoryIT {

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
    private ParticipantKeyValueRepository repository;

    @BeforeEach
    void initSchema() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS participant_key_value");
        jdbcTemplate.execute(
                "CREATE TABLE participant_key_value (" +
                        "study_id BIGINT NOT NULL, " +
                        "participant_id INTEGER NOT NULL, " +
                        "key TEXT NOT NULL, " +
                        "value JSONB NOT NULL, " +
                        "created TIMESTAMPTZ NOT NULL DEFAULT now(), " +
                        "modified TIMESTAMPTZ NOT NULL DEFAULT now(), " +
                        "CONSTRAINT pk_participant_key_value PRIMARY KEY (study_id, participant_id, key)" +
                        ")");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_participant_key_value_key ON participant_key_value(key)");
    }

    @Test
    @Order(1)
    void insert_and_get_and_getValue() {
        Long studyId = 1L;
        Integer participantId = 101;
        String key = "garmin.settings";
        Map<String, Object> value = new HashMap<>();
        value.put("enabled", true);
        value.put("threshold", 42);

        repository.insert(studyId, participantId, key, value);

        Optional<ParticipantKeyValue> rec = repository.get(studyId, participantId, key);
        assertThat(rec).isPresent();
        assertThat(rec.get().studyId()).isEqualTo(studyId);
        assertThat(rec.get().participantId()).isEqualTo(participantId);
        assertThat(rec.get().key()).isEqualTo(key);
        assertThat(rec.get().value()).containsEntry("enabled", true).containsEntry("threshold", 42);

        Optional<Map<String, Object>> val = repository.getValue(studyId, participantId, key);
        assertThat(val).isPresent();
        assertThat(val.get()).containsEntry("enabled", true).containsEntry("threshold", 42);
    }

    @Test
    @Order(2)
    void update_existing_and_non_existing() {
        Long studyId = 2L;
        Integer participantId = 202;
        String key = "prefs";
        Map<String, Object> value = Map.of("lang", "en");
        repository.insert(studyId, participantId, key, value);

        boolean updated = repository.update(studyId, participantId, key, Map.of("lang", "de"));
        assertThat(updated).isTrue();
        assertThat(repository.getValue(studyId, participantId, key)).isPresent();
        assertThat(repository.getValue(studyId, participantId, key).get()).containsEntry("lang", "de");

        boolean updatedMissing = repository.update(studyId, participantId, "missing", Map.of("x", 1));
        assertThat(updatedMissing).isFalse();
    }

    @Test
    @Order(3)
    void upsert_insert_then_update() {
        Long studyId = 3L;
        Integer participantId = 303;
        String key = "settings";
        Map<String, Object> v1 = Map.of("a", 1);
        Map<String, Object> v2 = Map.of("a", 2, "b", true);

        repository.upsert(studyId, participantId, key, v1);
        assertThat(repository.getValue(studyId, participantId, key)).isPresent();
        assertThat(repository.getValue(studyId, participantId, key).get()).containsEntry("a", 1);

        repository.upsert(studyId, participantId, key, v2);
        assertThat(repository.getValue(studyId, participantId, key)).isPresent();
        assertThat(repository.getValue(studyId, participantId, key).get()).containsEntry("a", 2).containsEntry("b", true);
    }

    @Test
    @Order(4)
    void getKeys_lists_all_for_participant() {
        Long studyId = 4L;
        Integer participantId = 404;
        repository.insert(studyId, participantId, "k1", Map.of("x", 1));
        repository.insert(studyId, participantId, "k2", Map.of("y", 2));
        repository.insert(studyId, 405, "other", Map.of());

        List<ParticipantKeyValue> keys = repository.getKeys(studyId, participantId);
        assertThat(keys).hasSize(2);
        assertThat(keys.stream().map(ParticipantKeyValue::key)).containsExactlyInAnyOrder("k1", "k2");
    }

    @Test
    @Order(5)
    void getByKey_across_studies_and_participants() {
        repository.insert(10L, 1, "shared", Map.of("a", 1));
        repository.insert(10L, 2, "shared", Map.of("b", 2));
        repository.insert(11L, 3, "shared", Map.of("c", 3));
        repository.insert(11L, 4, "other", Map.of("d", 4));

        List<ParticipantKeyValue> rows = repository.getByKey("shared");
        assertThat(rows).hasSize(3);
        assertThat(rows.stream().map(ParticipantKeyValue::participantId)).containsExactlyInAnyOrder(1, 2, 3);
        assertThat(rows.stream().map(ParticipantKeyValue::studyId)).containsExactlyInAnyOrder(10L, 10L, 11L);
    }

    @Test
    @Order(6)
    void delete_removes_and_returns_true_when_present() {
        Long studyId = 5L;
        Integer participantId = 505;
        String key = "tmp";
        repository.insert(studyId, participantId, key, Map.of("x", 1));

        boolean deleted = repository.delete(studyId, participantId, key);
        assertThat(deleted).isTrue();
        assertThat(repository.get(studyId, participantId, key)).isEmpty();

        boolean deletedAgain = repository.delete(studyId, participantId, key);
        assertThat(deletedAgain).isFalse();
    }

    @Test
    @Order(7)
    void delete_with_value_only_deletes_on_contained_match() {
        Long studyId = 6L;
        Integer participantId = 606;
        String key = "conf";

        Map<String, Object> stored = Map.of(
                "a", 1,
                "b", true,
                "meta", Map.of("x", 5)
        );
        repository.insert(studyId, participantId, key, stored);

        boolean deletedSubset = repository.delete(studyId, participantId, key, Map.of("a", 1));
        assertThat(deletedSubset).isTrue();
        assertThat(repository.get(studyId, participantId, key)).isEmpty();

        repository.insert(studyId, participantId, key, stored);
        boolean deletedNonMatch = repository.delete(studyId, participantId, key, Map.of("a", 2));
        assertThat(deletedNonMatch).isFalse();
        assertThat(repository.get(studyId, participantId, key)).isPresent();

        boolean deletedExact = repository.delete(studyId, participantId, key, stored);
        assertThat(deletedExact).isTrue();
        assertThat(repository.get(studyId, participantId, key)).isEmpty();
    }
}
