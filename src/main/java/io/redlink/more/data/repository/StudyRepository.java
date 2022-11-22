/*
 * Copyright (c) 2022 Redlink GmbH.
 */
package io.redlink.more.data.repository;

import io.redlink.more.data.model.ApiCredentials;
import io.redlink.more.data.model.Observation;
import io.redlink.more.data.model.ParticipantConsent;
import io.redlink.more.data.model.RoutingInfo;
import io.redlink.more.data.model.Study;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static io.redlink.more.data.repository.DbUtils.toInstant;
import static io.redlink.more.data.repository.DbUtils.toLocalDate;

@Service
public class StudyRepository {

    private static final String SQL_FIND_STUDY_BY_ID =
            "SELECT * FROM studies WHERE study_id = ?";

    private static final String SQL_LIST_OBSERVATIONS_BY_STUDY =
            "SELECT * FROM observations WHERE study_id = ? AND ( study_group_id IS NULL OR study_group_id = ? )";

    private static final String SQL_ROUTING_INFO_BY_REG_TOKEN =
            "SELECT pt.study_id as study_id, pt.participant_id as participant_id, study_group_id " +
            "FROM participants pt INNER JOIN registration_tokens rt ON (pt.study_id = rt.study_id and pt.participant_id = rt.participant_id) " +
            "WHERE rt.token = ?";
    private static final String SQL_ROUTING_INFO_BY_REG_TOKEN_WITH_LOCK =
            "SELECT pt.study_id as study_id, pt.participant_id as participant_id, study_group_id " +
            "FROM participants pt INNER JOIN registration_tokens rt ON (pt.study_id = rt.study_id and pt.participant_id = rt.participant_id) " +
            "WHERE rt.token = ? FOR UPDATE OF rt";

    private static final String SQL_CLEAR_TOKEN =
            "DELETE FROM registration_tokens WHERE token = ?";

    private static final String SQL_INSERT_CREDENTIALS =
            "WITH data as (SELECT :api_secret as api_secret, :study_id as study_id, :participant_id as participant_id) " +
            "INSERT INTO api_credentials (api_id, api_secret, study_id, participant_id) " +
            "SELECT md5(study_id::text || random()::text), api_secret, study_id, participant_id FROM data " +
            "RETURNING api_id";

    private static final String SQL_INSERT_STUDY_CONSENT =
            "INSERT INTO participation_consents(study_id, participant_id, accepted, origin, content_md5) VALUES (:study_id, :participant_id, :accepted, :origin, :content_md5) " +
            "ON CONFLICT (study_id, participant_id) DO UPDATE SET accepted = excluded.accepted, origin = excluded.origin, content_md5 = excluded.content_md5, consent_timestamp = now()";

    private static final String SQL_INSERT_OBSERVATION_CONSENT =
            "INSERT INTO observation_consents(study_id, participant_id, observation_id) VALUES (:study_id, :participant_id, :observation_id) " +
            "ON CONFLICT (study_id, participant_id, observation_id) DO NOTHING";


    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedTemplate;

    public StudyRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
    }

    private Optional<RoutingInfo> getRoutingInfo(String registrationToken, boolean lock) {
        var sql = lock ? SQL_ROUTING_INFO_BY_REG_TOKEN_WITH_LOCK : SQL_ROUTING_INFO_BY_REG_TOKEN;
        return jdbcTemplate.query(sql, getRoutingInfoMapper(), registrationToken).stream().findFirst();
    }


    public Optional<Study> findByRegistrationToken(String registrationToken) {
        final Optional<RoutingInfo> ri = getRoutingInfo(registrationToken, false);
        if (ri.isEmpty()) return Optional.empty();

        var routingInfo = ri.get();

        final List<Observation> observations = listObservations(routingInfo.studyId(), routingInfo.studyGroupId().orElse(-1));

        return Optional.ofNullable(
                jdbcTemplate.queryForObject(SQL_FIND_STUDY_BY_ID, getStudyRowMapper(observations), routingInfo.studyId())
        );
    }

    private List<Observation> listObservations(long studyId, int groupId) {
        return jdbcTemplate.query(SQL_LIST_OBSERVATIONS_BY_STUDY, getObservationRowMapper(), studyId, groupId);
    }

    @Transactional(readOnly = false)
    public Optional<ApiCredentials> createCredentials(String registrationToken, ParticipantConsent consent, Supplier<String> passwordSupplier) {
        final Optional<RoutingInfo> ri = getRoutingInfo(registrationToken, true);
        if (ri.isEmpty()) return Optional.empty();

        var routingInfo = ri.get();
        final String secret = passwordSupplier.get();

        storeConsent(routingInfo.studyId(), routingInfo.participantId(), consent);

        final String apiId = namedTemplate.queryForObject(SQL_INSERT_CREDENTIALS,
                new MapSqlParameterSource()
                        .addValue("api_secret", secret)
                        .addValue("study_id", routingInfo.studyId())
                        .addValue("participant_id", routingInfo.participantId()),
                (rs, row)-> rs.getString("api_id"));

        jdbcTemplate.update(SQL_CLEAR_TOKEN, registrationToken);

        return Optional.of(new ApiCredentials(apiId, secret));
    }

    private void storeConsent(long studyId, int participantId, ParticipantConsent consent) {

        namedTemplate.update(SQL_INSERT_STUDY_CONSENT, toParameterSource(studyId, participantId, consent));

        namedTemplate.batchUpdate(SQL_INSERT_OBSERVATION_CONSENT, consent.observationConsents().stream().map(c -> toParameterSource(studyId, participantId, c)).toArray(SqlParameterSource[]::new));
    }

    private static RowMapper<Study> getStudyRowMapper(List<Observation> observations) {
        return (rs, rowNum) -> new Study(
                rs.getLong("study_id"),
                rs.getString("title"),
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
                rs.getString("participant_info")
        );
    }

    private static RowMapper<RoutingInfo> getRoutingInfoMapper() {
        return ((row, rowNum) ->
                new RoutingInfo(
                        row.getLong("study_id"),
                        row.getInt("participant_id"),
                        DbUtils.readOptionalInt(row, "study_group_id")
                )
        );
    }

    private static MapSqlParameterSource toParameterSource(long studyId, int participantId, ParticipantConsent consent) {
        return new MapSqlParameterSource()
                .addValue("study_id", studyId)
                .addValue("participant_id", participantId)
                .addValue("accepted", consent.accepted())
                .addValue("origin", consent.deviceId())
                .addValue("content_md5", consent.consentMd5());
    }

    private static MapSqlParameterSource toParameterSource(long studyId, int participantId, ParticipantConsent.ObservationConsent consent) {
        return new MapSqlParameterSource()
                .addValue("study_id", studyId)
                .addValue("participant_id", participantId)
                .addValue("observation_id", consent.observationId())
                ;
    }

}
