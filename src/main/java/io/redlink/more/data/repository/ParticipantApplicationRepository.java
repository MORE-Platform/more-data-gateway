package io.redlink.more.data.repository;

import io.redlink.more.data.model.ParticipantApplication;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class ParticipantApplicationRepository {
    private static final String SELECT_BY_UUID =
            "SELECT * FROM participant_applications WHERE study_id = ? AND uuid = ?";

    private static final String DELETE_BY_PARTICIPANT =
            "DELETE FROM participant_applications WHERE study_id = ? AND participant_id = ?";

    private final JdbcTemplate template;

    public ParticipantApplicationRepository(JdbcTemplate template) {
        this.template = template;
    }

    public Optional<ParticipantApplication> findByUserDataReference(Long studyId, String userDataReference) {
        try {
            UUID uuid = UUID.fromString(userDataReference);
            return template.query(SELECT_BY_UUID, getRowMapper(), studyId, uuid)
                    .stream().findFirst();
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public void deleteAllByParticipant(Long studyId, Integer participantId) {
        template.update(DELETE_BY_PARTICIPANT, studyId, participantId);
    }

    private RowMapper<ParticipantApplication> getRowMapper() {
        return (rs, rowNum) -> new ParticipantApplication()
                .setStudyId(rs.getLong("study_id"))
                .setParticipantId(rs.getInt("participant_id"))
                .setApplication(rs.getString("application"))
                .setUuid(rs.getObject("uuid", UUID.class));
    }
}
