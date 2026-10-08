package io.redlink.more.data.repository;

import io.redlink.more.data.model.ObservationResyncRequest;
import io.redlink.more.data.model.ResyncInterval;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.List;

@Component
public class ObservationResyncRepository {

    private static final String LIST_PENDING = """
            SELECT r.study_id, r.participant_id, r.observation_id, o.type, r.resync_interval, r.resync_start, r.resync_end
            FROM observation_resync_requests r
                INNER JOIN observations o ON (o.study_id = r.study_id AND o.observation_id = r.observation_id)
            WHERE NOT r.synced
            ORDER BY r.created""";

    // the resync_start guard keeps a request that was renewed in the meantime
    private static final String MARK_SYNCED = """
            UPDATE observation_resync_requests SET synced = TRUE
            WHERE study_id = ? AND participant_id = ? AND observation_id = ? AND resync_start = ?""";

    private static final String MARK_SYNCED_BY_IDS = """
            UPDATE observation_resync_requests SET synced = TRUE
            WHERE study_id = ? AND participant_id = ? AND observation_id = ? AND NOT synced""";

    private static final String DELETE_REQUEST = """
            DELETE FROM observation_resync_requests
            WHERE study_id = ? AND participant_id = ? AND observation_id = ? AND resync_start = ?""";

    // NOTE: same merge as the Studymanager's ObservationResyncRequestRepository: restart now, keep the later end
    //       and the faster interval of a still running request
    private static final String UPSERT_REQUEST = """
            INSERT INTO observation_resync_requests AS r
                (study_id, participant_id, observation_id, resync_interval, resync_start, resync_end)
            VALUES (?, ?, ?, ?, now(), now() + ? * interval '1 second')
            ON CONFLICT (study_id, participant_id, observation_id) DO UPDATE SET
                resync_start = now(),
                resync_end = GREATEST(r.resync_end, EXCLUDED.resync_end),
                resync_interval = CASE WHEN r.resync_end > now()
                        AND array_position(ARRAY['urgent','high','normal','low'], r.resync_interval)
                          < array_position(ARRAY['urgent','high','normal','low'], EXCLUDED.resync_interval)
                    THEN r.resync_interval ELSE EXCLUDED.resync_interval END,
                synced = FALSE""";

    private final JdbcTemplate jdbcTemplate;

    public ObservationResyncRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ObservationResyncRequest> listPending() {
        return jdbcTemplate.query(LIST_PENDING, getRowMapper());
    }

    public int markSynced(ObservationResyncRequest request) {
        return jdbcTemplate.update(MARK_SYNCED,
                request.studyId(), request.participantId(), request.observationId(), Timestamp.from(request.start()));
    }

    /**
     * Marks a pending request synced, e.g. after the observation callback collected the data.
     */
    public int markSynced(long studyId, int participantId, int observationId) {
        return jdbcTemplate.update(MARK_SYNCED_BY_IDS, studyId, participantId, observationId);
    }

    public int delete(ObservationResyncRequest request) {
        return jdbcTemplate.update(DELETE_REQUEST,
                request.studyId(), request.participantId(), request.observationId(), Timestamp.from(request.start()));
    }

    /**
     * Requests a resync every {@code interval} from now for the given {@code duration}, merged into an existing request.
     */
    public void upsert(long studyId, int participantId, int observationId, ResyncInterval interval, Duration duration) {
        jdbcTemplate.update(UPSERT_REQUEST,
                studyId, participantId, observationId, interval.dbValue(), duration.toSeconds());
    }

    private static RowMapper<ObservationResyncRequest> getRowMapper() {
        return (rs, rowNum) -> new ObservationResyncRequest(
                rs.getLong("study_id"),
                rs.getInt("participant_id"),
                rs.getInt("observation_id"),
                rs.getString("type"),
                ResyncInterval.fromDb(rs.getString("resync_interval")),
                rs.getTimestamp("resync_start").toInstant(),
                rs.getTimestamp("resync_end").toInstant()
        );
    }
}
