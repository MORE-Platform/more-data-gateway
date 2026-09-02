package io.redlink.more.data.repository;

import io.redlink.more.data.model.ParticipantMilestone;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class ParticipantMilestoneRepository {

    private static final String GET_BY_IDS =
            "SELECT * FROM participant_milestones WHERE study_id = ? AND participant_id = ? AND milestone_id = ?";

    private final JdbcTemplate jdbcTemplate;

    public ParticipantMilestoneRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<ParticipantMilestone> getByIds(long studyId, int participantId, int milestoneId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    GET_BY_IDS, getRowMapper(), studyId, participantId, milestoneId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    private static RowMapper<ParticipantMilestone> getRowMapper() {
        return (rs, rowNum) -> new ParticipantMilestone(
                rs.getLong("study_id"),
                rs.getInt("participant_id"),
                rs.getInt("milestone_id"),
                rs.getInt("participant_milestone_id"),
                DbUtils.toInstant(rs.getTimestamp("date_time")),
                DbUtils.toInstant(rs.getTimestamp("created")),
                DbUtils.toInstant(rs.getTimestamp("modified"))
        );
    }
}
