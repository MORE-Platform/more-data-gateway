/*
 * Copyright (c) 2022 Redlink GmbH.
 */
package io.redlink.more.data.repository;

import io.redlink.more.data.model.*;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.util.Pair;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.security.core.parameters.P;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Supplier;

import static io.redlink.more.data.repository.DbUtils.toInstant;
import static io.redlink.more.data.repository.DbUtils.toLocalDate;

@Service
public class StudyRepository {

    private static final String SQL_FIND_STUDY_BY_ID =
            "SELECT * FROM studies WHERE study_id = ?";

    private static final String SQL_LIST_OBSERVATIONS_BY_STUDY =
            "SELECT * FROM observations WHERE study_id = ? AND ( study_group_id IS NULL OR study_group_id = ? )";

    private static final String SQL_ROUTING_INFO_BY_REG_TOKEN =
            "SELECT pt.study_id as study_id, pt.participant_id as participant_id, study_group_id, s.status = 'active' as is_active " +
            "FROM participants pt " +
            "  INNER JOIN registration_tokens rt ON (pt.study_id = rt.study_id and pt.participant_id = rt.participant_id) " +
            "  INNER JOIN studies s on (s.study_id = pt.study_id) " +
            "WHERE rt.token = ?";
    private static final String SQL_ROUTING_INFO_BY_REG_TOKEN_WITH_LOCK =
            SQL_ROUTING_INFO_BY_REG_TOKEN + " FOR UPDATE OF rt";

    private static final String SQL_CLEAR_TOKEN =
            "DELETE FROM registration_tokens WHERE token = ?";

    private static final String SQL_INSERT_CREDENTIALS =
            "WITH data as (SELECT :api_secret as api_secret, :study_id as study_id, :participant_id as participant_id) " +
            "INSERT INTO api_credentials (api_id, api_secret, study_id, participant_id) " +
            "SELECT md5(study_id::text || random()::text), api_secret, study_id, participant_id FROM data " +
            "RETURNING api_id";
    private static final String SQL_CLEAR_CREDENTIALS =
            "DELETE FROM api_credentials " +
            "WHERE api_id = :api_id " +
            "RETURNING study_id, participant_id";

    private static final String SQL_INSERT_STUDY_CONSENT =
            "INSERT INTO participation_consents(study_id, participant_id, accepted, origin, content_md5) VALUES (:study_id, :participant_id, :accepted, :origin, :content_md5) " +
            "ON CONFLICT (study_id, participant_id) DO " +
            "   UPDATE SET accepted = excluded.accepted, origin = excluded.origin, content_md5 = excluded.content_md5, " +
            "              consent_timestamp = now(), consent_withdrawn = NULL";
    private static final String SQL_WITHDRAW_STUDY_CONSENT =
            "UPDATE participation_consents " +
            "SET consent_withdrawn = now() " +
            "WHERE study_id = :study_id AND participant_id = :participant_id";

    private static final String SQL_INSERT_OBSERVATION_CONSENT =
            "INSERT INTO observation_consents(study_id, participant_id, observation_id) VALUES (:study_id, :participant_id, :observation_id) " +
            "ON CONFLICT (study_id, participant_id, observation_id) DO NOTHING";
    private static final String SQL_SET_PARTICIPANT_STATUS =
            "UPDATE participants " +
            "SET status = :newStatus::participant_status, modified = now() " +
            "WHERE study_id = :study_id AND participant_id = :participant_id AND status = :oldStatus::participant_status";

    private static final String GET_OBSERVATION_PROPERTIES_FOR_PARTICIPANT =
            "SELECT properties FROM participant_observation_properties " +
            "WHERE  study_id = ? AND participant_id = ? AND observation_id = ?";

    private static final String GET_API_ROUTING_INFO_BY_API_TOKEN =
            "SELECT t.study_id, t.observation_id, o.study_group_id, o.type, t.token, s.status = 'active' AS is_active " +
            "FROM observation_api_tokens t " +
                "INNER JOIN observations o ON (t.study_id = o.study_id AND t.observation_id = o.observation_id) " +
                "INNER JOIN studies s ON (t.study_id = s.study_id) " +
            "WHERE s.study_id = ? AND o.observation_id = ? AND t.token_id = ?";
    private static final String GET_PARTICIPANT_STUDY_GROUP = "SELECT study_group_id FROM participants WHERE study_id = ? AND participant_id = ?";

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedTemplate;

    public StudyRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
    }

    private Optional<RoutingInfo> getRoutingInfo(String registrationToken, boolean lock) {
        var sql = lock ? SQL_ROUTING_INFO_BY_REG_TOKEN_WITH_LOCK : SQL_ROUTING_INFO_BY_REG_TOKEN;
        try (var stream = jdbcTemplate.queryForStream(sql, getRoutingInfoMapper(), registrationToken)) {
            return stream.findFirst();
        }
    }

    public Optional<ApiRoutingInfo> getApiRoutingInfo(Long studyId, Integer observationId, Integer tokenId) {
        return jdbcTemplate.queryForStream(
                GET_API_ROUTING_INFO_BY_API_TOKEN,
                getApiRoutingInfoRowMapper(),
                studyId, observationId, tokenId
        ).findFirst();
    }

    public Optional<OptionalInt> getParticipantStudyGroupId(Long studyId, Integer participantId) {
        return jdbcTemplate.queryForStream(
                GET_PARTICIPANT_STUDY_GROUP,
                ((rs, rowNum) -> DbUtils.readOptionalInt(rs, "study_group_id")),
                studyId, participantId
        ).findFirst();
    }

    public Optional<Study> findByRegistrationToken(String registrationToken) {
        return getRoutingInfo(registrationToken, false)
                .flatMap(this::findStudy);
    }

    public Optional<Study> findStudy(RoutingInfo routingInfo) {
        final List<Observation> observations = listObservations(
                routingInfo.studyId(), routingInfo.studyGroupId().orElse(-1), routingInfo.participantId());

        try (var stream = jdbcTemplate.queryForStream(SQL_FIND_STUDY_BY_ID, getStudyRowMapper(observations), routingInfo.studyId())) {
            return stream.findFirst();
        }
    }

    private List<Observation> listObservations(long studyId, int groupId, int participantId) {
        return jdbcTemplate.query(SQL_LIST_OBSERVATIONS_BY_STUDY, getObservationRowMapper(), studyId, groupId).stream()
                .map(o -> mergeParticipantProperties(o, studyId, participantId))
                .toList();
    }

    private Observation mergeParticipantProperties(Observation observation, long studyId, int participantId) {
        return getParticipantProperties(studyId, participantId, observation.observationId())
                .map(props -> observation.withProperties(
                        DbUtils.mergeObjects(observation.properties(), props)))
                .orElse(observation);
    }

    public Optional<Object> getParticipantProperties(Long studyId, Integer participantId, Integer observationId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    GET_OBSERVATION_PROPERTIES_FOR_PARTICIPANT,
                    getParticipantObservationPropertiesRowMapper(),
                    studyId,
                    participantId,
                    observationId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    private static RowMapper<Object> getParticipantObservationPropertiesRowMapper() {
        return (rs, rowNum) -> DbUtils.readObject(rs,"properties");
    }

    @Transactional
    public Optional<String> createCredentials(String registrationToken, ParticipantConsent consent, Supplier<String> passwordSupplier) {
        final Optional<RoutingInfo> ri = getRoutingInfo(registrationToken, true);
        if (ri.isEmpty()) return Optional.empty();

        var routingInfo = ri.get();
        final String secret = passwordSupplier.get();

        storeConsent(routingInfo.studyId(), routingInfo.participantId(), consent);

        final String apiId = namedTemplate.queryForObject(SQL_INSERT_CREDENTIALS,
                toParameterSource(routingInfo.studyId(), routingInfo.participantId())
                        .addValue("api_secret", secret),
                (rs, row) -> rs.getString("api_id"));

        if (apiId != null) {
            jdbcTemplate.update(SQL_CLEAR_TOKEN, registrationToken);
            updateParticipantStatus(routingInfo.studyId(), routingInfo.participantId(), "new", "active");
            return Optional.of(apiId);
        }
        throw new IllegalStateException("Creating API-Credentials failed!");
    }

    private void updateParticipantStatus(long studyId, int particpantId, String oldStatus, String newStatus) {
        namedTemplate.update(SQL_SET_PARTICIPANT_STATUS,
                toParameterSource(studyId, particpantId)
                        .addValue("oldStatus", oldStatus)
                        .addValue("newStatus", newStatus)
        );
    }

    private void storeConsent(long studyId, int participantId, ParticipantConsent consent) {
        // Store Study-Consent
        namedTemplate.update(SQL_INSERT_STUDY_CONSENT, toParameterSource(studyId, participantId, consent));
        // Store Consent for individual Observations
        namedTemplate.batchUpdate(SQL_INSERT_OBSERVATION_CONSENT,
                consent.observationConsents().stream()
                        .map(c -> toParameterSource(studyId, participantId, c))
                        .toArray(SqlParameterSource[]::new));
    }

    private void withdrawConsent(long studyId, int participantId) {
        namedTemplate.update(SQL_WITHDRAW_STUDY_CONSENT, toParameterSource(studyId, participantId));
    }

    @Transactional
    public void clearCredentials(String apiId) {
        namedTemplate.query(SQL_CLEAR_CREDENTIALS,
                new MapSqlParameterSource()
                        .addValue("api_id", apiId),
                rs -> {
                    final long studyId = rs.getLong("study_id");
                    final int participantId = rs.getInt("participant_id");
                    withdrawConsent(studyId, participantId);
                    updateParticipantStatus(studyId, participantId,
                            "active", "abandoned");
                }
        );

    }

    private static RowMapper<Study> getStudyRowMapper(List<Observation> observations) {
        return (rs, rowNum) -> new Study(
                rs.getLong("study_id"),
                rs.getString("title"),
                "active".equalsIgnoreCase(rs.getString("status")),
                rs.getString("participant_info"),
                rs.getString("consent_info"),
                toLocalDate(rs.getDate("start_date")),
                toLocalDate(rs.getDate("planned_end_date")),
                observations,
                toInstant(rs.getTimestamp("created")),
                toInstant(rs.getTimestamp("modified"))
        );
    }

    private static RowMapper<Observation> getObservationRowMapper() {
        return (rs, rowNum) -> new Observation(
                rs.getInt("observation_id"),
                rs.getString("title"),
                rs.getString("type"),
                rs.getString("participant_info"),
                DbUtils.readObject(rs,"properties"),
                DbUtils.readEvent(rs, "schedule"),
                toInstant(rs.getTimestamp("created")),
                toInstant(rs.getTimestamp("modified"))
        );
    }

    private static RowMapper<RoutingInfo> getRoutingInfoMapper() {
        return ((row, rowNum) ->
                new RoutingInfo(
                        row.getLong("study_id"),
                        row.getInt("participant_id"),
                        DbUtils.readOptionalInt(row, "study_group_id"),
                        row.getBoolean("is_active")
                )
        );
    }

    private static RowMapper<ApiRoutingInfo> getApiRoutingInfoRowMapper() {
        return ((rs, rowNum) -> new ApiRoutingInfo(
                rs.getLong("study_id"),
                rs.getInt("observation_id"),
                rs.getString("type"),
                DbUtils.readOptionalInt(rs, "study_group_id"),
                rs.getBoolean("is_active"),
                rs.getString("token"))
        );
    }

    private static MapSqlParameterSource toParameterSource(long studyId, int participantId) {
        return new MapSqlParameterSource()
                .addValue("study_id", studyId)
                .addValue("participant_id", participantId)
                ;
    }

    private static MapSqlParameterSource toParameterSource(long studyId, int participantId, ParticipantConsent consent) {
        return toParameterSource(studyId, participantId)
                .addValue("accepted", consent.accepted())
                .addValue("origin", consent.deviceId())
                .addValue("content_md5", consent.consentMd5());
    }

    private static MapSqlParameterSource toParameterSource(long studyId, int participantId, ParticipantConsent.ObservationConsent consent) {
        return toParameterSource(studyId, participantId)
                .addValue("observation_id", consent.observationId())
                ;
    }
}
