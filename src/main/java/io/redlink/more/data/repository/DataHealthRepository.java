/*
 * Copyright LBI-DHP and/or licensed to LBI-DHP under one or more
 * contributor license agreements (LBI-DHP: Ludwig Boltzmann Institute
 * for Digital Health and Prevention -- A research institute of the
 * Ludwig Boltzmann Gesellschaft, Österreichische Vereinigung zur
 * Förderung der wissenschaftlichen Forschung).
 * Licensed under the Elastic License 2.0.
 */
package io.redlink.more.data.repository;

import io.redlink.more.data.model.DataHealth;
import io.redlink.more.data.model.ObservationDataHealth;
import io.redlink.more.data.model.ObservationDataState;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

@Component
public class DataHealthRepository {

    private static final String GET_BY_PK = """
            SELECT * FROM occurred_observation oo
                     WHERE oo.study_id = :study_id AND oo.participant_id = :participant_id AND oo.observation_id = :observation_id AND oo.start = :start
            """;
    private static final String LIST_OCCURRED_OBSERVATION = """
            SELECT * FROM occurred_observation oo
                    WHERE oo.study_id = :study_id
                        AND (:participant_id::INT IS NULL OR oo.participant_id = :participant_id)
                        AND (:observation_id::INT IS NULL OR oo.observation_id = :observation_id)
                        AND (:data_valid::BOOLEAN IS NULL OR oo.data_valid = :data_valid)
                        AND (:data_states::observation_data_state[] IS NULL OR oo.data_state = ANY(:data_states::observation_data_state[]))
                        AND (:start_time::TIMESTAMPTZ IS NULL OR oo.start >= :start_time)
                        AND (:end_time::TIMESTAMPTZ IS NULL OR oo."end" <= :end_time)
            """;

    private static final String FIND_LAST_START_TIME = """
            SELECT start FROM occurred_observation oo
                    WHERE oo.study_id = :study_id
                        AND (:participant_id::INT IS NULL OR oo.participant_id = :participant_id)
                        AND (:observation_id::INT IS NULL OR oo.observation_id = :observation_id)
                        AND (:data_valid::BOOLEAN IS NULL OR oo.data_valid = :data_valid)
                        AND (:data_states::observation_data_state[] IS NULL OR oo.data_state = ANY(:data_states::observation_data_state[]))
                    ORDER BY oo.start DESC
                    LIMIT 1
            """;

    private static String DELETE_BY_STUDY_ID = """
            DELETE FROM occurred_observation oo
            WHERE oo.study_id = :study_id
    """;

    private static final String DELETE_ALL = "DELETE FROM occurred_observation";
    private final JdbcTemplate template;
    private final NamedParameterJdbcTemplate namedTemplate;

    public DataHealthRepository(JdbcTemplate template) {
        this.template = template;
        this.namedTemplate = new NamedParameterJdbcTemplate(template);
    }


    public Optional<ObservationDataHealth> getByIds(long studyId, int participantId, int observationId, Instant start) {
        try {
            return Optional.ofNullable(namedTemplate.queryForObject(
                    GET_BY_PK,
                    new MapSqlParameterSource("study_id", studyId)
                            .addValue("participant_id", participantId)
                            .addValue("observation_id", observationId)
                            .addValue("start", OffsetDateTime.ofInstant(start, ZoneOffset.UTC), Types.TIMESTAMP_WITH_TIMEZONE)
                    ,
                    getObservationDataHealthRowMapper()));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Stream<ObservationDataHealth> listObservationDataHealth(
            Long studyId, Integer participantId, Integer observationId,
            Boolean dataValid,
            Set<ObservationDataState> dataStates,
            Instant startTime,
            Instant endTime
    ) {
        return namedTemplate.queryForStream(LIST_OCCURRED_OBSERVATION,
                new MapSqlParameterSource("study_id", studyId)
                        .addValue("participant_id", participantId)
                        .addValue("observation_id", observationId)
                        .addValue("data_valid", dataValid)
                        .addValue("data_states", dataStates == null ? null : dataStates.stream().map(ObservationDataState::getValue).toArray(String[]::new))
                        .addValue("start_time", startTime == null ? null : startTime.atOffset(ZoneOffset.UTC))
                        .addValue("end_time", endTime == null ? null : endTime.atOffset(ZoneOffset.UTC)),
                getObservationDataHealthRowMapper());
    }

    public Instant getLatestStartTime(
            Long studyId, Integer participantId, Integer observationId,
            Boolean dataValid, Set<ObservationDataState> dataStates
    ) {
        try {
            return namedTemplate.queryForObject(FIND_LAST_START_TIME,
                    new MapSqlParameterSource("study_id", studyId)
                            .addValue("participant_id", participantId)
                            .addValue("observation_id", observationId)
                            .addValue("data_valid", dataValid)
                            .addValue("data_states", dataStates == null ? null : dataStates.stream().map(ObservationDataState::getValue).toArray(String[]::new)),
                    getInstantRowMapper("start", true));
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    private static MapSqlParameterSource toParams(Long studyId) {
        return new MapSqlParameterSource()
                .addValue("study_id", studyId)
                ;
    }

    private static MapSqlParameterSource toParams(Long studyId, Integer participantId, Integer observationId) {
        return toParams(studyId)
                .addValue("participant_id", participantId)
                .addValue("observation_id", observationId);
    }

    private static RowMapper<ObservationDataHealth> getObservationDataHealthRowMapper() {
        return (rs, rowNum) -> new ObservationDataHealth(
                rs.getLong("study_id"),
                rs.getInt("observation_id"),
                rs.getInt("participant_id"),
                DbUtils.toInstant(rs.getTimestamp("start", DbUtils.tzUTC)),
                DbUtils.toInstant(rs.getTimestamp("end", DbUtils.tzUTC)),
                new DataHealth(
                    rs.getBoolean("data_valid"),
                    ObservationDataState.fromValue(rs.getString("data_state"))));
    }
    private static RowMapper<Instant> getInstantRowMapper(String field, boolean withTimezone) {
        return (rs, rowNum) -> withTimezone ? DbUtils.toInstant(rs.getTimestamp(field, DbUtils.tzUTC)) :
                DbUtils.toInstant(rs.getTimestamp(field));
    }

}
