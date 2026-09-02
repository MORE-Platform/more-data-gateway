package io.redlink.more.data.repository;

import io.redlink.more.data.model.ParticipantMilestone;
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

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@JdbcTest
@Import(ParticipantMilestoneRepository.class)
class ParticipantMilestoneRepositoryIT {

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
    private ParticipantMilestoneRepository repository;

    @BeforeEach
    void initSchema() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS participant_milestones");
        jdbcTemplate.execute(
                "CREATE TABLE participant_milestones (" +
                        "study_id BIGINT NOT NULL, " +
                        "participant_id INTEGER NOT NULL, " +
                        "milestone_id INTEGER NOT NULL, " +
                        "participant_milestone_id INTEGER NOT NULL, " +
                        "date_time TIMESTAMP NOT NULL, " +
                        "created TIMESTAMP NOT NULL DEFAULT now(), " +
                        "modified TIMESTAMP NOT NULL DEFAULT now(), " +
                        "CONSTRAINT pk_participant_milestones PRIMARY KEY (study_id, participant_id, milestone_id)" +
                        ")");
    }

    @Test
    void getByIds_returns_row_when_present() {
        long studyId = 1L;
        int participantId = 101;
        int milestoneId = 1;
        Instant dateTime = Instant.parse("2024-06-15T14:00:00Z");

        jdbcTemplate.update(
                "INSERT INTO participant_milestones(study_id, participant_id, milestone_id, participant_milestone_id, date_time) VALUES (?, ?, ?, ?, ?)",
                studyId, participantId, milestoneId, 1, java.sql.Timestamp.from(dateTime));

        Optional<ParticipantMilestone> result = repository.getByIds(studyId, participantId, milestoneId);
        assertThat(result).isPresent();
        assertThat(result.get().studyId()).isEqualTo(studyId);
        assertThat(result.get().participantId()).isEqualTo(participantId);
        assertThat(result.get().milestoneId()).isEqualTo(milestoneId);
        assertThat(result.get().dateTime()).isEqualTo(dateTime);
    }

    @Test
    void getByIds_returns_empty_when_missing() {
        assertThat(repository.getByIds(999L, 999, 999)).isEmpty();
    }
}
