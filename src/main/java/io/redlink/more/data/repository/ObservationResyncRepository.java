package io.redlink.more.data.repository;

import io.redlink.more.data.model.ObservationResyncRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ObservationResyncRepository {

    private static final String LIST_PENDING = """
            SELECT r.study_id, r.participant_id, r.observation_id, o.type
            FROM observation_resync_requests r
                INNER JOIN observations o ON (o.study_id = r.study_id AND o.observation_id = r.observation_id)
            ORDER BY r.created""";

    private static final String DELETE_REQUEST =
            "DELETE FROM observation_resync_requests WHERE study_id = ? AND participant_id = ? AND observation_id = ?";

    private final JdbcTemplate jdbcTemplate;

    public ObservationResyncRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ObservationResyncRequest> listPending() {
        return jdbcTemplate.query(LIST_PENDING, getRowMapper());
    }

    public int delete(ObservationResyncRequest request) {
        return jdbcTemplate.update(DELETE_REQUEST, request.studyId(), request.participantId(), request.observationId());
    }

    private static RowMapper<ObservationResyncRequest> getRowMapper() {
        return (rs, rowNum) -> new ObservationResyncRequest(
                rs.getLong("study_id"),
                rs.getInt("participant_id"),
                rs.getInt("observation_id"),
                rs.getString("type")
        );
    }
}
