/*
 * Copyright (c) 2022 Redlink GmbH.
 */
package io.redlink.more.data.repository;
import org.apache.commons.lang3.tuple.Pair;
import io.redlink.more.data.model.*;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Supplier;

import io.redlink.more.data.model.scheduler.Interval;
import io.redlink.more.data.model.scheduler.RelativeEvent;
import io.redlink.more.data.model.scheduler.ScheduleEvent;
import io.redlink.more.data.schedule.SchedulerUtils;
import org.springframework.dao.EmptyResultDataAccessException;
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
            "SELECT *, status IN ('active', 'preview') as study_active FROM studies WHERE study_id = ?";

    private static final String SQL_LIST_OBSERVATIONS_BY_STUDY =
            "SELECT * FROM observations WHERE study_id = ? AND ( study_group_id IS NULL OR study_group_id = ? )";

    private static final String SQL_LIST_OBSERVATIONS_BY_STUDY_WITH_ALL_OBSERVATIONS =
            "SELECT * FROM observations WHERE study_id = ?";

    private static final String SQL_ROUTING_INFO_BY_REG_TOKEN = """
            SELECT pt.study_id as study_id, pt.participant_id as participant_id, study_group_id,
                s.status IN ('active', 'preview') as study_active,
                pt.status = 'active' as participant_active
            FROM participants pt
                INNER JOIN registration_tokens rt ON (pt.study_id = rt.study_id and pt.participant_id = rt.participant_id)
                INNER JOIN studies s on (s.study_id = pt.study_id)
            WHERE rt.token = ?
            """;
    private static final String SQL_ROUTING_INFO_BY_REG_TOKEN_WITH_LOCK =
            SQL_ROUTING_INFO_BY_REG_TOKEN + " FOR UPDATE OF rt";
    private static final String GET_ROUTING_INFO = """
            SELECT pt.study_id as study_id, pt.participant_id as participant_id, study_group_id,
                s.status IN ('active', 'preview') as study_active,
                pt.status = 'active' as participant_active
            FROM participants pt
                INNER JOIN studies s on (s.study_id = pt.study_id)
            WHERE pt.study_id = ? AND pt.participant_id = ?
            """;

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
            "SET status = :newStatus::participant_status, start = :start, modified = now() " +
            "WHERE study_id = :study_id AND participant_id = :participant_id AND status = :oldStatus::participant_status";

    private static final String SQL_LIST_PARTICIPANTS_BY_STUDY =
            "SELECT participant_id, alias, status, sg.study_group_id, sg.title as study_group_title, start " +
            "FROM participants p LEFT OUTER JOIN study_groups sg ON ( p.study_id = sg.study_id AND p.study_group_id = sg.study_group_id ) " +
            "WHERE p.study_id = :study_id " +
                "AND (p.study_group_id = :study_group_id OR :study_group_id::INT IS NULL)";

    private static final String GET_OBSERVATION_PROPERTIES_FOR_PARTICIPANT =
            "SELECT properties FROM participant_observation_properties " +
            "WHERE  study_id = ? AND participant_id = ? AND observation_id = ?";

    private static final String GET_API_ROUTING_INFO_BY_API_TOKEN = """
            SELECT t.study_id, t.observation_id, o.study_group_id, o.type, t.token,
                s.status IN ('active', 'preview') AS study_active
            FROM observation_api_tokens t
                INNER JOIN observations o ON (t.study_id = o.study_id AND t.observation_id = o.observation_id)
                INNER JOIN studies s ON (t.study_id = s.study_id)
            WHERE s.study_id = ? AND o.observation_id = ? AND t.token_id = ?
            """;

    private static final String GET_OBSERVATION_SCHEDULE = "SELECT schedule FROM observations WHERE study_id = ? AND observation_id = ?";

    private static final String GET_PARTICIPANT_INFO_AND_START_DURATION_END_FOR_STUDY_AND_PARTICIPANT =
            "SELECT start, participant_id, alias, COALESCE(sg.duration, s.duration) AS duration, s.planned_end_date FROM participants p " +
            "LEFT OUTER JOIN study_groups sg on p.study_id = sg.study_id and p.study_group_id = sg.study_group_id " +
            "JOIN studies s on p.study_id = s.study_id " +
            "WHERE p.study_id = ? AND participant_id = ?";

    private static final String GET_DURATION_INFO_FOR_STUDY =
            "SELECT sg.study_group_id as groupid, sg.duration AS groupduration, s.duration AS studyduration, s.planned_end_date AS enddate, s.planned_start_date AS startdate FROM studies s " +
            "LEFT OUTER JOIN study_groups sg on s.study_id = sg.study_id " +
            "WHERE s.study_id = ?";

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedTemplate;

    public StudyRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
    }

    public Optional<RoutingInfo> getRoutingInfo(Long studyId, Integer participantId) {
        try (var stream = jdbcTemplate.queryForStream(GET_ROUTING_INFO, getRoutingInfoMapper(), studyId, participantId)) {
            return stream.findFirst();
        }
    }

    private Optional<RoutingInfo> getRoutingInfo(String registrationToken, boolean lock) {
        var sql = lock ? SQL_ROUTING_INFO_BY_REG_TOKEN_WITH_LOCK : SQL_ROUTING_INFO_BY_REG_TOKEN;
        try (var stream = jdbcTemplate.queryForStream(sql, getRoutingInfoMapper(), registrationToken)) {
            return stream.findFirst();
        }
    }

    public Optional<ApiRoutingInfo> getApiRoutingInfo(Long studyId, Integer observationId, Integer tokenId) {
        try(var stream = jdbcTemplate.queryForStream(
                GET_API_ROUTING_INFO_BY_API_TOKEN,
                getApiRoutingInfoRowMapper(),
                studyId, observationId, tokenId
        )) {
            return stream.findFirst();
        }
    }

    public Optional<ScheduleEvent> getObservationSchedule(Long studyId, Integer observationId) {
        try (var stream = jdbcTemplate.queryForStream(
                GET_OBSERVATION_SCHEDULE,
                getObservationScheduleRowMapper(),
                studyId, observationId
        )) {
            return stream.findFirst();
        }
    }

    public Optional<Study> findByRegistrationToken(String registrationToken) {
        return getRoutingInfo(registrationToken, false)
                .flatMap(this::findStudy);
    }

    public Optional<Study> findStudy(RoutingInfo routingInfo) {
        return findStudy(routingInfo, true);
    }

    public Optional<Study> findStudy(RoutingInfo routingInfo, boolean filterObservationsByGroup) {
        final List<Observation> observations = listObservations(
                routingInfo.studyId(), routingInfo.studyGroupId().orElse(-1), routingInfo.participantId(),filterObservationsByGroup);

        final SimpleParticipant participant = findParticipant(routingInfo).orElse(null);

        try (var stream = jdbcTemplate.queryForStream(SQL_FIND_STUDY_BY_ID, getStudyRowMapper(observations, participant), routingInfo.studyId())) {
            return stream.findFirst();
        }
    }

    public Optional<SimpleParticipant> findParticipant(RoutingInfo routingInfo) {
        try (var stream = jdbcTemplate.queryForStream(GET_PARTICIPANT_INFO_AND_START_DURATION_END_FOR_STUDY_AND_PARTICIPANT,
                (rs, rowNum) -> {
                    Instant start = Optional.ofNullable(rs.getTimestamp("start"))
                            .map(Timestamp::toInstant).orElse(null);
                    Instant end = Optional.ofNullable(DbUtils.readDuration(rs, "duration"))
                            .map(d -> d.getEnd(start))
                            .orElse(Instant.ofEpochMilli(rs.getDate("planned_end_date").getTime()));
                    return new SimpleParticipant(
                            rs.getInt("participant_id"),
                            rs.getString("alias"),
                            start,
                            end
                    );
                }
                , routingInfo.studyId(), routingInfo.participantId())) {
            return stream.findFirst();
        }
    }

    public List<Participant> listParticipants(long studyId, OptionalInt groupId) {
        return namedTemplate.query(
                SQL_LIST_PARTICIPANTS_BY_STUDY,
                new MapSqlParameterSource()
                        .addValue("study_id", studyId)
                        .addValue("study_group_id", groupId.isPresent() ? groupId.getAsInt() : null),
                getParticipantRowMapper());
    }

    private List<Observation> listObservations(long studyId, int groupId, int participantId, boolean filterByGroup) {
        if(filterByGroup) {
            return jdbcTemplate.query(SQL_LIST_OBSERVATIONS_BY_STUDY, getObservationRowMapper(), studyId, groupId).stream()
                    .map(o -> mergeParticipantProperties(o, studyId, participantId))
                    .toList();
        } else {
            return jdbcTemplate.query(SQL_LIST_OBSERVATIONS_BY_STUDY_WITH_ALL_OBSERVATIONS, getObservationRowMapper(), studyId).stream()
                    .map(o -> mergeParticipantProperties(o, studyId, participantId))
                    .toList();
        }
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

    private static RowMapper<ScheduleEvent> getObservationScheduleRowMapper() {
        return (rs, rowNum) -> DbUtils.readEvent(rs, "schedule");
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
            updateParticipantStatus(routingInfo.studyId(), routingInfo.studyGroupId().orElse(0), routingInfo.participantId(), "new", "active");
            return Optional.of(apiId);
        }
        throw new IllegalStateException("Creating API-Credentials failed!");
    }

    private void updateParticipantStatus(long studyId, int groupId, int participantId, String oldStatus, String newStatus) {
        Timestamp start = null;

        if ("active".equals(newStatus)) {
            start = Timestamp.from(
                    SchedulerUtils.shiftStartIfObservationAlreadyEnded(Instant.now(), listObservations(studyId, groupId, participantId, true))
            );
        }

        namedTemplate.update(SQL_SET_PARTICIPANT_STATUS,
                toParameterSource(studyId, participantId)
                        .addValue("start", start)
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
                    updateParticipantStatus(studyId, participantId, 0,
                            "active", "abandoned");
                }
        );

    }

    private static RowMapper<Study> getStudyRowMapper(List<Observation> observations, SimpleParticipant participant) {
        return (rs, rowNum) -> new Study(
                rs.getLong("study_id"),
                rs.getString("title"),
                rs.getBoolean("study_active"),
                rs.getString("participant_info"),
                rs.getString("finish_text"),
                rs.getString("status"),
                rs.getString("consent_info"),
                readContact(rs),
                toLocalDate(rs.getDate("start_date")),
                toLocalDate(rs.getDate("planned_start_date")),
                toLocalDate(rs.getDate("planned_end_date")),
                observations,
                toInstant(rs.getTimestamp("created")),
                toInstant(rs.getTimestamp("modified")),
                participant
        );
    }

    private static Contact readContact(ResultSet rs) throws SQLException {
        return new Contact(
                rs.getString("institute"),
                rs.getString("contact_person"),
                rs.getString("contact_email"),
                rs.getString("contact_phone")
        );
    }

    private static RowMapper<Observation> getObservationRowMapper() {
        return (rs, rowNum) -> new Observation(
                rs.getInt("observation_id"),
                rs.getInt("study_group_id"),
                rs.getString("title"),
                rs.getString("type"),
                rs.getString("participant_info"),
                DbUtils.readObject(rs,"properties"),
                DbUtils.readEvent(rs, "schedule"),
                toInstant(rs.getTimestamp("created")),
                toInstant(rs.getTimestamp("modified")),
                rs.getBoolean("hidden"),
                rs.getBoolean("no_schedule")
        );
    }

    private static RowMapper<Participant> getParticipantRowMapper() {
        return (rs, rowNul) -> new Participant(
                rs.getInt("participant_id"),
                rs.getString("alias"),
                rs.getString("status"),
                DbUtils.readOptionalInt(rs, "study_group_id"),
                rs.getString("study_group_title"),
                toInstant(rs.getTimestamp("start"))
        );
    }

    private static RowMapper<RoutingInfo> getRoutingInfoMapper() {
        return ((row, rowNum) ->
                new RoutingInfo(
                        row.getLong("study_id"),
                        row.getInt("participant_id"),
                        DbUtils.readOptionalInt(row, "study_group_id"),
                        row.getBoolean("study_active"),
                        row.getBoolean("participant_active")
                )
        );
    }

    private static RowMapper<ApiRoutingInfo> getApiRoutingInfoRowMapper() {
        return ((rs, rowNum) -> new ApiRoutingInfo(
                rs.getLong("study_id"),
                rs.getInt("observation_id"),
                rs.getString("type"),
                DbUtils.readOptionalInt(rs, "study_group_id"),
                rs.getBoolean("study_active"),
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

    public Interval getInterval(Long studyId, Integer participantId, RelativeEvent event) {
        try(var stream = jdbcTemplate.queryForStream(
                GET_PARTICIPANT_INFO_AND_START_DURATION_END_FOR_STUDY_AND_PARTICIPANT,
                ((rs, rowNum) -> {
                    Instant start = rs.getTimestamp("start").toInstant();
                    // TODO correct sql.Date to Instant with Time 0 ?!
                    Instant end = Optional.ofNullable(DbUtils.readDuration(rs, "duration"))
                            .map(d -> d.getEnd(start))
                            .orElse(Instant.ofEpochMilli(rs.getDate("planned_end_date").getTime()));
                    return new Interval(start, SchedulerUtils.getEnd(event, start, end));

                }),
                studyId, participantId
        )) {
            return stream.findFirst().orElse(null);
        }
    }

    public Optional<StudyDurationInfo> getStudyDurationInfo(Long studyId) {
        return jdbcTemplate.query(GET_DURATION_INFO_FOR_STUDY,
                ((rs, rowNum) -> new StudyDurationInfo()
                        .setEndDate(rs.getDate("enddate").toLocalDate())
                        .setStartDate(rs.getDate("startdate").toLocalDate())
                        .setDuration(DbUtils.readDuration(rs, "studyduration"))
                        .addGroupDuration(Pair.of(rs.getInt("groupid"), DbUtils.readDuration(rs, "groupduration"))
                )), studyId).stream()
                .reduce((prev, curr) -> prev.addGroupDuration(curr.getGroupDurations().get(0)));
    }
}
