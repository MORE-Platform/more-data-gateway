/*
 * Copyright (c) 2022 Redlink GmbH.
 */
package io.redlink.more.data.repository;

import io.redlink.more.data.model.*;
import io.redlink.more.data.model.scheduler.ScheduleEvent;
import io.redlink.more.data.service.garmin.GarminService;
import io.redlink.more.data.util.MapperUtils;
import io.redlink.more.data.util.RandomSchedulerUtils;
import io.redlink.more.data.util.SchedulerUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static io.redlink.more.data.repository.DbUtils.toInstant;
import static io.redlink.more.data.repository.DbUtils.toLocalDate;
import static io.redlink.more.data.util.RandomSchedulerUtils.OBSERVATION_SCHEDULE_SEED_KEY;

@Service
public class StudyRepository {
    private static final Logger LOG = LoggerFactory.getLogger(StudyRepository.class);

    private static final String SQL_FIND_STUDY_BY_ID =
            "SELECT *, status IN ('active', 'preview') as study_active FROM studies WHERE study_id = ?";

    private static final String LIST_OBSERVATIONS_BY_STUDY_FOR_GROUP = """
            SELECT o.*, ARRAY_AGG(oog.observation_group_id) FILTER (WHERE oog.observation_group_id IS NOT NULL) AS observation_group_ids
            FROM observations o
                LEFT JOIN observation_observation_groups oog ON o.study_id = oog.study_id AND o.observation_id = oog.observation_id
            WHERE o.study_id = :study_id
              AND (o.study_group_id IS NULL OR o.study_group_id = :study_group_id)
              AND (NOT EXISTS (
                SELECT 1 FROM observation_observation_groups oog3
                WHERE oog3.study_id = o.study_id
                  AND oog3.observation_id = o.observation_id
                ) OR EXISTS (
                  SELECT 1 FROM observation_observation_groups oog2
                  WHERE oog2.study_id = o.study_id
                    AND oog2.observation_id = o.observation_id
                    AND oog2.observation_group_id = ANY(:observation_group_ids)))
            GROUP BY o.study_id, o.observation_id""";

    private static final String LIST_OBSERVATIONS_BY_STUDY_FOR_GROUP_ONLY_GROUPS = """
            SELECT o.*, ARRAY_AGG(oog.observation_group_id) FILTER (WHERE oog.observation_group_id IS NOT NULL) AS observation_group_ids
            FROM observations o
                LEFT JOIN observation_observation_groups oog ON o.study_id = oog.study_id AND o.observation_id = oog.observation_id
            WHERE o.study_id = :study_id
              AND (o.study_group_id IS NULL OR o.study_group_id = :study_group_id)
              AND (
                NOT EXISTS (
                  SELECT 1 FROM observation_observation_groups oog3
                  WHERE oog3.study_id = o.study_id
                    AND oog3.observation_id = o.observation_id
                ) OR EXISTS (
                  SELECT 1 FROM observation_observation_groups oog2
                  WHERE oog2.study_id = o.study_id
                    AND oog2.observation_id = o.observation_id
                    AND oog2.observation_group_id = ANY(:observation_group_ids)
                )
              )
            GROUP BY o.study_id, o.observation_id
            """;


    private static final String LIST_OBSERVATIONS_BY_STUDY_WITH_ALL_OBSERVATIONS =
            """
                    SELECT o.*,
                           ARRAY_AGG(oog.observation_group_id)
                             FILTER (WHERE oog.observation_group_id IS NOT NULL) AS observation_group_ids
                    FROM observations o
                    LEFT JOIN observation_observation_groups oog
                      ON o.study_id = oog.study_id AND o.observation_id = oog.observation_id
                    WHERE o.study_id = ?
                    GROUP BY o.study_id, o.observation_id
                    """;

    private static final String SQL_ROUTING_INFO_BY_REG_TOKEN = """
            SELECT pt.study_id as study_id, pt.participant_id as participant_id, study_group_id,
                s.status IN ('active', 'preview') as study_active,
                pt.status = 'active' as participant_active,
                (SELECT ARRAY_AGG(pog.observation_group_id)
                          FROM participant_observation_groups pog
                          WHERE pog.study_id = pt.study_id AND pog.participant_id = pt.participant_id) AS observation_group_ids
            FROM participants pt
                INNER JOIN registration_tokens rt ON (pt.study_id = rt.study_id and pt.participant_id = rt.participant_id)
                INNER JOIN studies s on (s.study_id = pt.study_id)
            WHERE rt.token = ?
            """;
    private static final String SQL_ROUTING_INFO_BY_REG_TOKEN_WITH_LOCK =
            SQL_ROUTING_INFO_BY_REG_TOKEN + " FOR UPDATE OF rt";
    private static final String SQL_PARTICIPANT_ID_AND_OBSERVATION_ID_BY_OBSERVATION_TOKEN = """
            SELECT pop.participant_id, pop.observation_id
            FROM participant_observation_properties pop
            WHERE pop.study_id = ?
              AND pop.properties ->> 'token' = ?
            LIMIT 1
            """;
    private static final String SQL_STUDY_ID_PARTICIPANT_ID_AND_OBSERVATION_ID_BY_OBSERVATION_TOKEN = """
            SELECT pop.study_id, pop.participant_id, pop.observation_id
            FROM participant_observation_properties pop
            WHERE pop.properties ->> 'token' = ?
            LIMIT 1
            """;
    private static final String GET_ROUTING_INFO = """
            SELECT pt.study_id as study_id, pt.participant_id as participant_id, study_group_id,
                s.status IN ('active', 'preview') as study_active,
                pt.status = 'active' as participant_active,
                (SELECT ARRAY_AGG(pog.observation_group_id)
                          FROM participant_observation_groups pog
                          WHERE pog.study_id = pt.study_id AND pog.participant_id = pt.participant_id) AS observation_group_ids
            FROM participants pt
                INNER JOIN studies s on (s.study_id = pt.study_id)
            WHERE pt.study_id = ? AND pt.participant_id = ?
            """;

    private static final String SQL_CLEAR_TOKEN =
            "DELETE FROM registration_tokens WHERE token = ?";

    private static final String SQL_INSERT_CREDENTIALS =
            """
                    WITH data as (SELECT :api_secret as api_secret, :study_id as study_id, :participant_id as participant_id)
                    INSERT INTO api_credentials (api_id, api_secret, study_id, participant_id)
                    SELECT md5(study_id::text || random()::text), api_secret, study_id, participant_id FROM data
                    RETURNING api_id""";
    private static final String SQL_CLEAR_CREDENTIALS =
            """
                    DELETE FROM api_credentials
                    WHERE api_id = :api_id
                    RETURNING study_id, participant_id""";

    private static final String SQL_INSERT_STUDY_CONSENT =
            """
                    INSERT INTO participation_consents(study_id, participant_id, accepted, origin, content_md5) VALUES (:study_id, :participant_id, :accepted, :origin, :content_md5)
                    ON CONFLICT (study_id, participant_id) DO
                       UPDATE SET accepted = excluded.accepted, origin = excluded.origin, content_md5 = excluded.content_md5,
                                  consent_timestamp = now(), consent_withdrawn = NULL""";
    private static final String SQL_WITHDRAW_STUDY_CONSENT =
            """
                    UPDATE participation_consents
                    SET consent_withdrawn = now()
                    WHERE study_id = :study_id AND participant_id = :participant_id""";

    private static final String SQL_GET_CONSENT = """
            SELECT accepted, origin, content_md5, consent_timestamp, consent_withdrawn
            FROM participation_consents
            WHERE study_id = :study_id AND participant_id = :participant_id""";

    private static final String SQL_GET_OBSERVATION_CONSENTS = """
            SELECT observation_id
            FROM observation_consents
            WHERE study_id = :study_id AND participant_id = :participant_id""";

    private static final String SQL_INSERT_OBSERVATION_CONSENT =
            """
                    INSERT INTO observation_consents(study_id, participant_id, observation_id) VALUES (:study_id, :participant_id, :observation_id)
                    ON CONFLICT (study_id, participant_id, observation_id) DO NOTHING""";
    private static final String SQL_SET_PARTICIPANT_STATUS =
            """
                    UPDATE participants
                    SET status = :newStatus::participant_status, start = COALESCE(:start, start), modified = now()
                    WHERE study_id = :study_id AND participant_id = :participant_id AND status = :oldStatus::participant_status""";

    private static final String SQL_LIST_PARTICIPANTS_BY_STUDY =
            """
                    SELECT
                        p.participant_id, p.study_id, p.alias, p.status, p.created, p.start, p.modified,
                        sg.study_group_id, sg.title as study_group_title,
                        ARRAY_AGG(pog.observation_group_id) FILTER (WHERE pog.observation_group_id IS NOT NULL) AS observation_group_ids
                    FROM participants p
                        LEFT OUTER JOIN study_groups sg ON ( p.study_id = sg.study_id AND p.study_group_id = sg.study_group_id )
                        LEFT JOIN participant_observation_groups pog ON p.study_id = pog.study_id AND p.participant_id = pog.participant_id
                    WHERE p.study_id = :study_id
                    AND (p.study_group_id = :study_group_id OR :study_group_id::INT IS NULL)
                    GROUP BY p.study_id, p.participant_id, sg.study_group_id, sg.title
                    HAVING :observation_group_ids::INT[] IS NULL OR COUNT(CASE WHEN pog.observation_group_id = ANY(:observation_group_ids) THEN 1 END) > 0;""";

    private static final String GET_OBSERVATION_PROPERTIES_FOR_PARTICIPANT =
            """
                    SELECT properties FROM participant_observation_properties
                    WHERE  study_id = ? AND participant_id = ? AND observation_id = ?""";

    //TODO: Needs adaptation after #251 DB changes!!
    private static final String GET_API_ROUTING_INFO_BY_API_TOKEN = """
            SELECT t.study_id, t.observation_id, o.study_group_id, o.observation_group_id, o.type, t.token,
                s.status IN ('active', 'preview') AS study_active,
                ARRAY_AGG(oog.observation_group_id) FILTER (WHERE oog.observation_group_id IS NOT NULL) AS observation_group_ids
            FROM observation_api_tokens t
                INNER JOIN observations o ON (t.study_id = o.study_id AND t.observation_id = o.observation_id)
                INNER JOIN studies s ON (t.study_id = s.study_id)
                LEFT JOIN observation_observation_groups oog ON t.study_id = oog.study_id AND t.observation_id = oog.observation_id
            WHERE s.study_id = ? AND o.observation_id = ? AND t.token_id = ?
            """;

    private static final String GET_OBSERVATION_SCHEDULE = "SELECT schedule FROM observations WHERE study_id = ? AND observation_id = ?";
    private static final String GET_PARTICIPANT_INFO_AND_START_DURATION_END_FOR_STUDY_AND_PARTICIPANT =
            """
                    SELECT start, participant_id, alias, COALESCE(sg.duration, s.duration) AS duration, s.planned_end_date FROM participants p
                    LEFT OUTER JOIN study_groups sg on p.study_id = sg.study_id and p.study_group_id = sg.study_group_id
                    JOIN studies s on p.study_id = s.study_id
                    WHERE p.study_id = ? AND participant_id = ?""";

    private static final String GET_DURATION_INFO_FOR_STUDY =
            """
                    SELECT sg.study_group_id as groupid, sg.duration AS groupduration, s.duration AS studyduration, s.planned_end_date AS enddate, s.planned_start_date AS startdate FROM studies s
                    LEFT OUTER JOIN study_groups sg on s.study_id = sg.study_id
                    WHERE s.study_id = ?""";

    private static final String SET_OBSERVATION_PROPERTIES_FOR_PARTICIPANT = "INSERT INTO participant_observation_properties(study_id,participant_id,observation_id,properties) VALUES (:study_id,:participant_id,:observation_id,:properties::jsonb) ON CONFLICT (study_id, participant_id, observation_id) DO UPDATE SET properties = EXCLUDED.properties";


    private static final String GET_PARTICIPANT_OBSERVATION_PROPERTIES_BY_PROPERTY_VALUE =
            "SELECT * FROM participant_observation_properties WHERE properties#>>CAST(:jsonPath AS text[]) = :value";

    private static final String GET_PARTICIPANT_OBSERVATION_PROPERTIES_BY_KEY_EXISTS =
            "SELECT * FROM participant_observation_properties WHERE participant_id = :participant_id AND study_id = :study_id AND properties ?? :key";

    private static final String GET_ALL_PARTICIPANT_OBSERVATION_PROPERTIES_BY_KEY_EXISTS =
            "SELECT * FROM participant_observation_properties WHERE properties ?? :key";

    private static final String GET_PARTICIPANT_OBSERVATIONS_PROPERTIES = "SELECT * FROM participant_observation_properties WHERE participant_id = :participant_id AND study_id = :study_id";

    private static final String SQL_LIST_OBSERVATIONS_BY_STUDY_AND_TYPES =
            """
                    SELECT o.*,
                           ARRAY_AGG(oog.observation_group_id)
                             FILTER (WHERE oog.observation_group_id IS NOT NULL) AS observation_group_ids
                    FROM observations o
                    LEFT JOIN observation_observation_groups oog
                      ON o.study_id = oog.study_id AND o.observation_id = oog.observation_id
                    WHERE o.study_id = :study_id
                      AND (o.study_group_id IS NULL OR o.study_group_id = :study_group_id)
                      AND o.type IN (:types)
                    GROUP BY o.study_id, o.observation_id
                    """;

    private static final String SQL_LIST_OBSERVATIONS_BY_STUDY_AND_TYPES_ONLY_GROUPS =
            """
                    SELECT o.*,
                           ARRAY_AGG(oog.observation_group_id)
                             FILTER (WHERE oog.observation_group_id IS NOT NULL) AS observation_group_ids
                    FROM observations o
                    LEFT JOIN observation_observation_groups oog
                      ON o.study_id = oog.study_id AND o.observation_id = oog.observation_id
                    WHERE o.study_id = :study_id
                      AND (o.study_group_id IS NULL OR o.study_group_id = :study_group_id)
                      AND o.type IN (:types)
                      AND (
                        NOT EXISTS (
                          SELECT 1 FROM observation_observation_groups oog3
                          WHERE oog3.study_id = o.study_id
                            AND oog3.observation_id = o.observation_id
                        ) OR EXISTS (
                          SELECT 1 FROM observation_observation_groups oog2
                          WHERE oog2.study_id = o.study_id
                            AND oog2.observation_id = o.observation_id
                            AND oog2.observation_group_id = ANY(:observation_group_ids)
                        )
                      )
                    GROUP BY o.study_id, o.observation_id
                    """;


    private static final String GET_ALL_PARTICIPANT_OBSERVATION_PROPERTIES_BY_OBSERVATION_TYPE_LIKE =
            """
                    SELECT pop.*
                    FROM participant_observation_properties AS pop
                    JOIN observations AS o
                      ON o.observation_id = pop.observation_id
                    WHERE o.type LIKE :observation_type""";


    private static final String SQL_LIST_OBSERVATIONS_BY_STUDY_AND_TYPES_WITH_ALL_OBSERVATIONS =
            """
                    SELECT o.*,
                           ARRAY_AGG(oog.observation_group_id)
                             FILTER (WHERE oog.observation_group_id IS NOT NULL) AS observation_group_ids
                    FROM observations o
                    LEFT JOIN observation_observation_groups oog
                      ON o.study_id = oog.study_id AND o.observation_id = oog.observation_id
                    WHERE o.study_id = :study_id
                      AND o.type IN (:types)
                    GROUP BY o.study_id, o.observation_id
                    """;

    private static final String SQL_LIST_OBSERVATIONS_BY_STUDY_AND_TYPES_WITH_ALL_OBSERVATIONS_ONLY_GROUPS =
            """
                    SELECT o.*,
                           ARRAY_AGG(oog.observation_group_id)
                             FILTER (WHERE oog.observation_group_id IS NOT NULL) AS observation_group_ids
                    FROM observations o
                    LEFT JOIN observation_observation_groups oog
                      ON o.study_id = oog.study_id AND o.observation_id = oog.observation_id
                    WHERE o.study_id = :study_id
                      AND o.type IN (:types)
                      AND (
                        NOT EXISTS (
                          SELECT 1 FROM observation_observation_groups oog3
                          WHERE oog3.study_id = o.study_id
                            AND oog3.observation_id = o.observation_id
                        ) OR EXISTS (
                          SELECT 1 FROM observation_observation_groups oog2
                          WHERE oog2.study_id = o.study_id
                            AND oog2.observation_id = o.observation_id
                            AND oog2.observation_group_id = ANY(:observation_group_ids)
                        )
                      )
                    GROUP BY o.study_id, o.observation_id
                    """;
    private static final String GET_PARTICIPANT_STATE =
            """
                    SELECT status FROM participants WHERE study_id = ? AND participant_id = ?""";

    private static final String GET_STUDY_STATE =
            """
                    SELECT status FROM studies WHERE study_id = ?""";

    private static final String GET_OBSERVATION_GROUP_BY_IDS = "SELECT * FROM observation_groups WHERE study_id = ? AND observation_group_id = ?";


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

    public Optional<RoutingInfoWithObservation> getRoutingInfoAndObservationIdByToken(long studyId, String token) {
        try {
            Pair<Integer, Integer> participantAndObservationId = jdbcTemplate.queryForObject(
                    SQL_PARTICIPANT_ID_AND_OBSERVATION_ID_BY_OBSERVATION_TOKEN,
                    (rs, rowNum) -> Pair.of(
                            rs.getInt("participant_id"),
                            rs.getInt("observation_id")
                    ),
                    studyId, token
            );

            if (participantAndObservationId == null) {
                LOG.warn("Participant and observation id not found for token {} in study {}", token, studyId);
                return Optional.empty();
            }

            return getRoutingInfo(studyId, participantAndObservationId.getLeft())
                    .map(routingInfo -> new RoutingInfoWithObservation(routingInfo, participantAndObservationId.getRight()));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Optional<RoutingInfoWithObservation> getRoutingInfoAndObservationIdByToken(String token) {
        try {
            var studyParticipantAndObservationId = jdbcTemplate.queryForObject(
                    SQL_STUDY_ID_PARTICIPANT_ID_AND_OBSERVATION_ID_BY_OBSERVATION_TOKEN,
                    (rs, rowNum) -> new Object() {
                        final long studyId = rs.getLong("study_id");
                        final int participantId = rs.getInt("participant_id");
                        final int observationId = rs.getInt("observation_id");
                    },
                    token
            );

            if (studyParticipantAndObservationId == null) {
                LOG.warn("Study, participant and observation id not found for token {}", token);
                return Optional.empty();
            }

            return getRoutingInfo(studyParticipantAndObservationId.studyId, studyParticipantAndObservationId.participantId)
                    .map(routingInfo -> new RoutingInfoWithObservation(routingInfo, studyParticipantAndObservationId.observationId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    private Optional<RoutingInfo> getRoutingInfo(String registrationToken, boolean lock) {
        var sql = lock ? SQL_ROUTING_INFO_BY_REG_TOKEN_WITH_LOCK : SQL_ROUTING_INFO_BY_REG_TOKEN;
        try (var stream = jdbcTemplate.queryForStream(sql, getRoutingInfoMapper(), registrationToken)) {
            return stream.findFirst();
        }
    }

    public Optional<ApiRoutingInfo> getApiRoutingInfo(Long studyId, Integer observationId, Integer tokenId) {
        try (var stream = jdbcTemplate.queryForStream(
                GET_API_ROUTING_INFO_BY_API_TOKEN,
                getApiRoutingInfoRowMapper(),
                studyId, observationId, tokenId
        )) {
            return stream.findFirst();
        }
    }

    public Optional<ScheduleEvent> getObservationSchedule(Long studyId, Integer observationId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    GET_OBSERVATION_SCHEDULE,
                    getObservationScheduleRowMapper(),
                    studyId, observationId
            ));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
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
        final SimpleParticipant participant = findParticipant(routingInfo).orElse(null);

        final List<Observation> observations = listObservations(
                routingInfo.studyId(),
                routingInfo.studyGroupId().orElse(-1),
                routingInfo.observationGroupIds(),
                routingInfo.participantId(),
                filterObservationsByGroup);

        try (var stream = jdbcTemplate.queryForStream(SQL_FIND_STUDY_BY_ID, getStudyRowMapper(observations, participant), routingInfo.studyId())) {
            var study = stream.findFirst();
            if (study.isPresent()) {
                generateRandomEventSchedulesForParticipant(routingInfo, observations);
            }
            return study;
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

    /**
     *
     * @param studyId             the study id
     * @param groupId             the study group id or NULL to deactivate this filter
     * @param observationGroupIds the list of observation groups (ANY if multiple) or empty/null to deactivate this filter
     * @return the list of participants based on the parsed filters
     */
    public List<Participant> listParticipants(long studyId, OptionalInt groupId, Set<Integer> observationGroupIds) {
        return namedTemplate.query(
                        SQL_LIST_PARTICIPANTS_BY_STUDY,
                        new MapSqlParameterSource()
                                .addValue("study_id", studyId)
                                .addValue("study_group_id", groupId.isPresent() ? groupId.getAsInt() : null)
                                .addValue("observation_group_ids", observationGroupIds != null && !observationGroupIds.isEmpty() ? observationGroupIds.toArray(new Integer[0]) : null),
                        getParticipantRowMapper()).stream()
                .map(p -> {
                    if (p.observationGroups() == null) {
                        return p;
                    } else { //we need to load the ObservationGroup titles for the Ids
                        return new Participant(
                                p.id(),
                                p.alias(),
                                p.status(),
                                p.studyGroupId(),
                                p.studyGroupTitle(),
                                p.start(),
                                p.observationGroups().stream()
                                        .map(og -> getObservationGroupById(studyId, p.id()))
                                        .toList());
                    }
                })
                .toList();
    }

    public Optional<String> getParticipantState(long studyId, int participantId) {
        return jdbcTemplate.query(
                        GET_PARTICIPANT_STATE,
                        (rs, rowNum) ->
                                rs.getString("status"),
                        studyId,
                        participantId)
                .stream()
                .findFirst();
    }

    public Optional<String> getStudyState(long studyId) {
        return jdbcTemplate.query(
                        GET_STUDY_STATE,
                        (rs, rowNum) ->
                                rs.getString("status"),
                        studyId)
                .stream()
                .findFirst();
    }

    private List<Observation> listObservations(long studyId, int studyGroupId, Collection<Integer> observationGroupIds, int participantId, boolean filterByGroup, Set<Integer> restrictToObservationGroupIds) {
        if (filterByGroup) {
            // NOTE: same as StudyManagerBackend: ObservationRepository#listObservationsForGroup
            final boolean restrictByGroups = restrictToObservationGroupIds != null && !restrictToObservationGroupIds.isEmpty();
            final String sql = restrictByGroups ? LIST_OBSERVATIONS_BY_STUDY_FOR_GROUP_ONLY_GROUPS : LIST_OBSERVATIONS_BY_STUDY_FOR_GROUP;
            final Integer[] groupIds = restrictByGroups
                    ? restrictToObservationGroupIds.toArray(new Integer[0])
                    : (observationGroupIds == null ? new Integer[0] : observationGroupIds.toArray(new Integer[0]));

            return namedTemplate.query(
                            sql,
                            new MapSqlParameterSource("study_id", studyId)
                                    .addValue("study_group_id", studyGroupId)
                                    .addValue("observation_group_ids", groupIds),
                            getObservationRowMapper()).stream()
                    .map(o -> mergeParticipantProperties(o, studyId, participantId))
                    .toList();
        } else {
            // no group-filtering in the SQL path; caller can still filter in-memory if needed
            return jdbcTemplate.query(LIST_OBSERVATIONS_BY_STUDY_WITH_ALL_OBSERVATIONS, getObservationRowMapper(), studyId).stream()
                    .map(o -> mergeParticipantProperties(o, studyId, participantId))
                    .toList();
        }
    }

    private List<Observation> listObservations(long studyId, int studyGroupId, Collection<Integer> observationGroupIds, int participantId, boolean filterByGroup) {
        return listObservations(studyId, studyGroupId, observationGroupIds, participantId, filterByGroup, null);
    }

    public List<Observation> filterObservations(RoutingInfo routingInfo, boolean filterByGroup, Predicate<Observation> filter, Set<Integer> restrictToObservationGroupIds) {
        return listObservations(
                routingInfo.studyId(),
                routingInfo.studyGroupId().orElse(0),
                routingInfo.observationGroupIds(),
                routingInfo.participantId(),
                filterByGroup,
                restrictToObservationGroupIds
        ).stream()
                .filter(filter)
                .toList();
    }

    public List<Observation> getObservationsByTypes(RoutingInfo routingInfo, Set<String> observationTypes, Set<Integer> restrictToObservationGroupIds) {
        if (observationTypes == null || observationTypes.isEmpty()) {
            return List.of();
        }

        final boolean restrictByGroups = restrictToObservationGroupIds != null && !restrictToObservationGroupIds.isEmpty();
        final Integer[] groupIds = restrictByGroups
                ? restrictToObservationGroupIds.toArray(new Integer[0])
                : (routingInfo.observationGroupIds() == null ? new Integer[0] : routingInfo.observationGroupIds().toArray(new Integer[0]));

        var params = new MapSqlParameterSource()
                .addValue("study_id", routingInfo.studyId())
                .addValue("study_group_id", routingInfo.studyGroupId().orElse(0))
                .addValue("types", observationTypes)
                .addValue("observation_group_ids", groupIds);

        return namedTemplate.query(SQL_LIST_OBSERVATIONS_BY_STUDY_AND_TYPES_ONLY_GROUPS, params, getObservationRowMapper())
                .stream()
                .map(o -> mergeParticipantProperties(o, routingInfo.studyId(), routingInfo.participantId()))
                .toList();
    }

    public List<Observation> filterObservations(Long studyId, Integer participantId, Predicate<Observation> filter) {
        return listObservations(studyId, 0, Collections.emptySet(), participantId, false)
                .stream()
                .filter(filter)
                .toList();
    }

    private Observation mergeParticipantProperties(Observation observation, long studyId, int participantId) {
        return getParticipantProperties(studyId, participantId, observation.observationId())
                .map(props -> observation.withProperties(
                        DbUtils.mergeObjects(observation.properties(), props)))
                .orElse(observation);
    }

    public void mergeParticipantProperties(Long studyId, Integer participantId, Integer observationId, Map<String, Object> properties) {
        var oldProps = getParticipantWithObservationProperties(studyId, participantId, observationId);
        MapSqlParameterSource data = new MapSqlParameterSource()
                .addValue("study_id", studyId)
                .addValue("participant_id", participantId)
                .addValue("observation_id", observationId)
                .addValue("properties", MapperUtils.writeValueAsString(
                        DbUtils.mergeObjects(oldProps.orElse(Map.of()), properties))
                );

        namedTemplate.update(SET_OBSERVATION_PROPERTIES_FOR_PARTICIPANT, data);
    }

    public void removeParticipantPropertyKey(Long studyId, Integer participantId, Integer observationId, String keyToRemove) {
        Optional<Object> currentProps = getParticipantProperties(studyId, participantId, observationId);

        if (currentProps.isPresent() && currentProps.get() instanceof Map) {
            Map<String, Object> properties = new HashMap<>((Map<String, Object>) currentProps.get());
            properties.remove(keyToRemove);

            MapSqlParameterSource data = new MapSqlParameterSource()
                    .addValue("study_id", studyId)
                    .addValue("participant_id", participantId)
                    .addValue("observation_id", observationId)
                    .addValue("properties", MapperUtils.writeValueAsString(properties));

            namedTemplate.update(SET_OBSERVATION_PROPERTIES_FOR_PARTICIPANT, data);
        }
    }


    public List<ParticipantWithObservationProperties> getParticipantByGarminStatus(String state) {
        if (state == null || state.isEmpty()) {
            return List.of();
        }
        try {
            return namedTemplate.query(
                    GET_PARTICIPANT_OBSERVATION_PROPERTIES_BY_PROPERTY_VALUE,
                    new MapSqlParameterSource()
                            .addValue("jsonPath", "{" + GarminService.AUTH_VALUES_KEY + ",state}")
                            .addValue("value", state),
                    getParticipantWithObservationPropertiesRowMapper());
        } catch (EmptyResultDataAccessException e) {
            return Collections.emptyList();
        }
    }

    public List<ParticipantWithObservationProperties> getParticipantObservationPropertiesByKeyExists(Long studyId, Integer participantId, String key) {
        if (key == null || key.isEmpty()) {
            return List.of();
        }
        try {
            return namedTemplate.query(
                    GET_PARTICIPANT_OBSERVATION_PROPERTIES_BY_KEY_EXISTS,
                    new MapSqlParameterSource()
                            .addValue("study_id", studyId)
                            .addValue("participant_id", participantId)
                            .addValue("key", key),
                    getParticipantWithObservationPropertiesRowMapper());
        } catch (EmptyResultDataAccessException e) {
            return Collections.emptyList();
        }
    }

    public List<ParticipantWithObservationProperties> getParticipantObservationPropertiesByKeyExists(String key) {
        if (key == null || key.isEmpty()) {
            return List.of();
        }
        try {
            return namedTemplate.query(
                    GET_ALL_PARTICIPANT_OBSERVATION_PROPERTIES_BY_KEY_EXISTS,
                    new MapSqlParameterSource()
                            .addValue("key", key),
                    getParticipantWithObservationPropertiesRowMapper());
        } catch (EmptyResultDataAccessException e) {
            return Collections.emptyList();
        }
    }

    public List<ParticipantWithObservationProperties> getAllParticpantObservationProperties(Long studyId, Integer participantId) {
        try {
            return namedTemplate.query(
                    GET_PARTICIPANT_OBSERVATIONS_PROPERTIES,
                    new MapSqlParameterSource()
                            .addValue("study_id", studyId)
                            .addValue("participant_id", participantId),
                    getParticipantWithObservationPropertiesRowMapper()
            ).stream().toList();
        } catch (EmptyResultDataAccessException e) {
            return Collections.emptyList();
        }
    }

    public Optional<Map<String, Object>> getParticipantWithObservationProperties(Long studyId, Integer participantId, int observationId) {
        try {
            return namedTemplate.query(
                    GET_PARTICIPANT_OBSERVATIONS_PROPERTIES,
                    new MapSqlParameterSource()
                            .addValue("study_id", studyId)
                            .addValue("participant_id", participantId)
                            .addValue("observation_id", observationId),
                    (rs, rowNum) ->
                            (Map<String, Object>) MapperUtils.readValue(rs.getObject("properties"), Map.class)
            ).stream().findFirst();
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public List<ParticipantWithObservationProperties> getParticipantByGarminAccessToken(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        try {
            return namedTemplate.query(
                    GET_PARTICIPANT_OBSERVATION_PROPERTIES_BY_PROPERTY_VALUE,
                    new MapSqlParameterSource()
                            .addValue("jsonPath", "{" + GarminService.USER_ACCESS_TOKEN_KEY + ",accessToken,accessToken}")
                            .addValue("value", token),
                    getParticipantWithObservationPropertiesRowMapper());
        } catch (EmptyResultDataAccessException e) {
            return Collections.emptyList();
        }
    }

    private static RowMapper<ParticipantWithObservationProperties> getParticipantWithObservationPropertiesRowMapper() {
        return (rs, rowNum) -> new ParticipantWithObservationProperties(
                rs.getInt("participant_id"),
                rs.getLong("study_id"),
                rs.getInt("observation_id"),
                (Map<String, Object>) MapperUtils.readValue(rs.getString("properties"), Map.class)
        );
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

    public List<ParticipantWithObservationProperties> getParticipantObservationPropertiesByObservationTypeLike(String observationType) {
        try {
            var params = new MapSqlParameterSource()
                    .addValue("observation_type", observationType);

            return namedTemplate.query(
                    GET_ALL_PARTICIPANT_OBSERVATION_PROPERTIES_BY_OBSERVATION_TYPE_LIKE,
                    params,
                    getParticipantWithObservationPropertiesMapper()
            );
        } catch (EmptyResultDataAccessException e) {
            return Collections.emptyList();
        }
    }

    private static RowMapper<Object> getParticipantObservationPropertiesRowMapper() {
        return (rs, rowNum) -> DbUtils.readObject(rs, "properties");
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
            updateParticipantStatus(routingInfo, "new", "invited");
            updateParticipantStatus(routingInfo, "invited", "active");
            return Optional.of(apiId);
        }
        throw new IllegalStateException("Creating API-Credentials failed!");
    }

    public void updateParticipantStatus(RoutingInfo routingInfo, String oldStatus, String newStatus) {
        updateParticipantStatus(routingInfo.studyId(), routingInfo.studyGroupId().orElse(0), routingInfo.observationGroupIds(), routingInfo.participantId(), oldStatus, newStatus);
    }

    private void updateParticipantStatus(long studyId, int studyGroupId, Collection<Integer> observationGroupIds, int participantId, String oldStatus, String newStatus) {
        Timestamp start = null;

        if ("active".equals(newStatus)) {
            start = Timestamp.from(
                    SchedulerUtils.shiftStartIfObservationAlreadyEnded(Instant.now(), listObservations(studyId, studyGroupId, observationGroupIds, participantId, true))
            );
        }

        var parameterSource = toParameterSource(studyId, participantId)
                .addValue("oldStatus", oldStatus)
                .addValue("newStatus", newStatus)
                .addValue("start", start);

        namedTemplate.update(SQL_SET_PARTICIPANT_STATUS, parameterSource);
    }

    public void storeConsent(long studyId, int participantId, ParticipantConsent consent) {
        // Store Study-Consent
        namedTemplate.update(SQL_INSERT_STUDY_CONSENT, toParameterSource(studyId, participantId, consent));
        // Store Consent for individual Observations
        namedTemplate.batchUpdate(SQL_INSERT_OBSERVATION_CONSENT,
                consent.observationConsents().stream()
                        .map(c -> toParameterSource(studyId, participantId, c))
                        .toArray(SqlParameterSource[]::new));
    }

    public Optional<ParticipantConsent> getConsent(long studyId, Integer participantId) {
        try {
            return namedTemplate.query(SQL_GET_CONSENT,
                    new MapSqlParameterSource()
                            .addValue("study_id", studyId)
                            .addValue("participant_id", participantId),
                    rs -> {
                        if (rs.next()) {
                            return Optional.of(new ParticipantConsent(
                                    rs.getBoolean("accepted"),
                                    rs.getString("origin"),
                                    rs.getString("content_md5"),
                                    toInstant(rs.getTimestamp("consent_timestamp")),
                                    getObservationConsents(studyId, participantId)
                            ));
                        }
                        return Optional.empty();
                    });
        } catch (DataAccessException e) {
            return Optional.empty();
        }
    }

    private List<ParticipantConsent.ObservationConsent> getObservationConsents(long studyId, int participantId) {
        return namedTemplate.query(SQL_GET_OBSERVATION_CONSENTS,
                new MapSqlParameterSource()
                        .addValue("study_id", studyId)
                        .addValue("participant_id", participantId),
                (rs, rowNum) -> new ParticipantConsent.ObservationConsent(
                        rs.getInt("observation_id"),
                        null
                ));
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
                    updateParticipantStatus(studyId, 0, null, participantId,
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
                DbUtils.readObject(rs, "properties"),
                DbUtils.readEvent(rs, "schedule"),
                toInstant(rs.getTimestamp("created")),
                toInstant(rs.getTimestamp("modified")),
                rs.getBoolean("hidden"),
                rs.getBoolean("no_schedule"),
                rs.getBoolean("reminder"),
                DbUtils.readSet(rs, "observation_group_ids", Integer.class)
        );
    }

    private static RowMapper<Participant> getParticipantRowMapper() {
        return (rs, rowNul) -> new Participant(
                rs.getInt("participant_id"),
                rs.getString("alias"),
                rs.getString("status"),
                DbUtils.readOptionalInt(rs, "study_group_id"),
                rs.getString("study_group_title"),
                toInstant(rs.getTimestamp("start")),
                DbUtils.readSet(rs, "observation_group_ids", Integer.class).stream()
                        .map(id -> new Participant.ObservationGroupInfo(id, null))
                        .collect(Collectors.toSet())
        );
    }

    private static RowMapper<RoutingInfo> getRoutingInfoMapper() {
        return ((row, rowNum) ->
                new RoutingInfo(
                        row.getLong("study_id"),
                        row.getInt("participant_id"),
                        DbUtils.readOptionalInt(row, "study_group_id"),
                        DbUtils.readSet(row, "observation_group_ids", Integer.class),
                        row.getBoolean("study_active"),
                        row.getBoolean("participant_active")
                )
        );
    }

    private static RowMapper<ParticipantWithObservationProperties> getParticipantWithObservationPropertiesMapper() {
        return (row, rowNum) -> new ParticipantWithObservationProperties(
                row.getInt("participant_id"),
                row.getLong("study_id"),
                row.getInt("observation_id"),
                (Map<String, Object>) MapperUtils.readValue(row.getObject("properties"), Map.class)
        );
    }

    private static RowMapper<ApiRoutingInfo> getApiRoutingInfoRowMapper() {
        return ((rs, rowNum) -> new ApiRoutingInfo(
                rs.getLong("study_id"),
                rs.getInt("observation_id"),
                rs.getString("type"),
                DbUtils.readOptionalInt(rs, "study_group_id"),
                DbUtils.readSet(rs, "observation_group_ids", Integer.class),
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
                .addValue("observation_id", consent.observationId());
    }


    public Optional<Instant> getStudyStartFor(Long studyId, Integer participantId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    GET_PARTICIPANT_INFO_AND_START_DURATION_END_FOR_STUDY_AND_PARTICIPANT,
                    (rs, rowNum) -> rs.getTimestamp("start").toInstant(),
                    studyId, participantId
            ));
        } catch (DataAccessException e) {
            return Optional.empty();
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

    public Participant.ObservationGroupInfo getObservationGroupById(long studyId, int observationGroupId) {
        try {
            return jdbcTemplate.queryForObject(GET_OBSERVATION_GROUP_BY_IDS, getObservationGroupInfoRowMapper(), studyId, observationGroupId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    private static RowMapper<Participant.ObservationGroupInfo> getObservationGroupInfoRowMapper() {
        return (rs, rowNum) -> new Participant.ObservationGroupInfo(
                rs.getInt("observation_group_id"),
                rs.getString("title"));
    }


    private void generateRandomEventSchedulesForParticipant(RoutingInfo routingInfo, List<Observation> observations) {
        var participantObservationProperties = getAllParticpantObservationProperties(routingInfo.studyId(), routingInfo.participantId())
                .stream()
                .filter(p ->
                        p.properties().containsKey(OBSERVATION_SCHEDULE_SEED_KEY)
                                && p.properties().get(OBSERVATION_SCHEDULE_SEED_KEY) != null)
                .map(ParticipantWithObservationProperties::observationId)
                .collect(Collectors.toSet());
        observations
                .stream()
                .filter(observation -> !participantObservationProperties.contains(observation.observationId()))
                .forEach(observation -> {
                    ScheduleEvent event = observation.observationSchedule();
                    if (event == null || event.getRandomization() == null || !event.getRandomization().state()) {
                        return;
                    }
                    String userId = routingInfo.studyId() + "_" + routingInfo.participantId() + "_" + observation.observationId();
                    Long seed = RandomSchedulerUtils.generateSeedFromSchedule(event, userId);
                    if (seed == null) {
                        return;
                    }
                    Map<String, Object> seedObject = Map.of(OBSERVATION_SCHEDULE_SEED_KEY, seed);
                    mergeParticipantProperties(routingInfo.studyId(), routingInfo.participantId(), observation.observationId(), seedObject);
                });
    }
}
