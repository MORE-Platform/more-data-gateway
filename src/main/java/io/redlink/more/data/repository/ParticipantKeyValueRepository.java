package io.redlink.more.data.repository;

import io.redlink.more.data.model.ParticipantKeyValue;
import io.redlink.more.data.util.MapperUtils;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ParticipantKeyValueRepository {

    private static final String SQL_INSERT =
            """
                    INSERT INTO participant_key_value(study_id, participant_id, key, value)
                    VALUES (:study_id, :participant_id, :key, :value::jsonb)
                    """;

    private static final String SQL_UPDATE =
            """
                    UPDATE participant_key_value
                    SET value = :value::jsonb, modified = now()
                    WHERE study_id = :study_id AND participant_id = :participant_id AND key = :key
                    """;

    private static final String SQL_UPSERT =
            """
                    INSERT INTO participant_key_value(study_id, participant_id, key, value)
                    VALUES (:study_id, :participant_id, :key, :value::jsonb)
                    ON CONFLICT (study_id, participant_id, key)
                    DO UPDATE SET value = EXCLUDED.value, modified = now()
                    """;

    private static final String SQL_SELECT =
            "SELECT value FROM participant_key_value WHERE study_id = ? AND participant_id = ? AND key = ?";

    private static final String SQL_SELECT_RECORD =
            """
                    SELECT pkv.study_id, pkv.participant_id, pkv.key, pkv.value,
                           ARRAY_AGG(pog.observation_group_id) FILTER (WHERE pog.observation_group_id IS NOT NULL) AS observation_group_ids
                    FROM participant_key_value pkv
                    LEFT JOIN participant_observation_groups pog
                      ON pkv.study_id = pog.study_id AND pkv.participant_id = pog.participant_id
                    WHERE pkv.study_id = ? AND pkv.participant_id = ? AND pkv.key = ?
                    GROUP BY pkv.study_id, pkv.participant_id, pkv.key, pkv.value
                    """;

    private static final String SQL_DELETE =
            "DELETE FROM participant_key_value WHERE study_id = ? AND participant_id = ? AND key = ?";

    private static final String SQL_DELETE_WITH_KEY_TYPE =
            "DELETE FROM participant_key_value WHERE study_id = ? AND participant_id = ? AND key = ? AND value @> ?::jsonb";

    private static final String SQL_SELECT_ALL =
            """
                    SELECT pkv.study_id, pkv.participant_id, pkv.key, pkv.value,
                           ARRAY_AGG(pog.observation_group_id) FILTER (WHERE pog.observation_group_id IS NOT NULL) AS observation_group_ids
                    FROM participant_key_value pkv
                    LEFT JOIN participant_observation_groups pog
                      ON pkv.study_id = pog.study_id AND pkv.participant_id = pog.participant_id
                    WHERE pkv.study_id = ? AND pkv.participant_id = ?
                    GROUP BY pkv.study_id, pkv.participant_id, pkv.key, pkv.value
                    """;

    private static final String SQL_GET_BY_KEY =
            """
                    SELECT pkv.study_id, pkv.participant_id, pkv.key, pkv.value,
                           ARRAY_AGG(pog.observation_group_id) FILTER (WHERE pog.observation_group_id IS NOT NULL) AS observation_group_ids
                    FROM participant_key_value pkv
                    LEFT JOIN participant_observation_groups pog
                      ON pkv.study_id = pog.study_id AND pkv.participant_id = pog.participant_id
                    WHERE pkv.key = ?
                    GROUP BY pkv.study_id, pkv.participant_id, pkv.key, pkv.value
                    """;

    private static final String SQL_GET_WITH_KEY_TYPE =
            """
                    SELECT pkv.study_id, pkv.participant_id, pkv.key, pkv.value,
                           ARRAY_AGG(pog.observation_group_id) FILTER (WHERE pog.observation_group_id IS NOT NULL) AS observation_group_ids
                    FROM participant_key_value pkv
                    LEFT JOIN participant_observation_groups pog
                      ON pkv.study_id = pog.study_id AND pkv.participant_id = pog.participant_id
                    WHERE pkv.study_id = ? AND pkv.participant_id = ?
                    GROUP BY pkv.study_id, pkv.participant_id, pkv.key, pkv.value
                    """;

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedTemplate;

    public ParticipantKeyValueRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
    }

    public void insert(Long studyId, Integer participantId, String key, Map<String, Object> value) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("study_id", studyId)
                .addValue("participant_id", participantId)
                .addValue("key", key)
                .addValue("value", MapperUtils.writeValueAsString(value));
        namedTemplate.update(SQL_INSERT, params);
    }

    public boolean update(Long studyId, Integer participantId, String key, Map<String, Object> value) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("study_id", studyId)
                .addValue("participant_id", participantId)
                .addValue("key", key)
                .addValue("value", MapperUtils.writeValueAsString(value));
        return namedTemplate.update(SQL_UPDATE, params) > 0;
    }

    public void upsert(Long studyId, Integer participantId, String key, Map<String, Object> value) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("study_id", studyId)
                .addValue("participant_id", participantId)
                .addValue("key", key)
                .addValue("value", MapperUtils.writeValueAsString(value));
        namedTemplate.update(SQL_UPSERT, params);
    }

    public Optional<ParticipantKeyValue> get(Long studyId, Integer participantId, String key) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    SQL_SELECT_RECORD,
                    getRecordRowMapper(studyId, participantId, key),
                    studyId, participantId, key
            ));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }


    public List<ParticipantKeyValue> getKeys(Long studyId, Integer participantId) {
        try {
            return jdbcTemplate.query(
                            SQL_SELECT_ALL,
                            getRecordRowMapper(studyId, participantId),
                            studyId, participantId
                    )
                    .stream()
                    .toList();
        } catch (EmptyResultDataAccessException e) {
            return Collections.emptyList();
        }
    }

    public Optional<Map<String, Object>> getValue(Long studyId, Integer participantId, String key) {
        try {
            var valueObj = jdbcTemplate.queryForObject(
                    SQL_SELECT,
                    getValueRowMapper(),
                    studyId, participantId, key
            );
            return Optional.ofNullable((Map<String, Object>) valueObj);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public List<ParticipantKeyValue> getByKey(String key) {
        try {
            return jdbcTemplate.query(
                    SQL_GET_BY_KEY,
                    getRecordRowMapper(),
                    key
            );
        } catch (EmptyResultDataAccessException e) {
            return Collections.emptyList();
        }

    }

    public List<ParticipantKeyValue> getKeysWithValue(Long studyId, Integer participantId) {
        try {
            return jdbcTemplate.query(
                    SQL_GET_WITH_KEY_TYPE,
                    getRecordRowMapper(studyId, participantId),
                    studyId, participantId
            );
        } catch (EmptyResultDataAccessException e) {
            return Collections.emptyList();
        }
    }

    public boolean delete(Long studyId, Integer participantId, String key) {
        return jdbcTemplate.update(SQL_DELETE, studyId, participantId, key) > 0;
    }

    public boolean delete(Long studyId, Integer participantId, String key, Map<String, Object> value) {
        return jdbcTemplate.update(SQL_DELETE_WITH_KEY_TYPE, studyId, participantId, key, MapperUtils.writeValueAsString(value)) > 0;
    }

    private static RowMapper<Object> getValueRowMapper() {
        return (rs, rowNum) -> DbUtils.readObject(rs, "value");
    }

    private static RowMapper<ParticipantKeyValue> getRecordRowMapper(Long defaultStudyId, Integer defaultParticipantId) {
        return (rs, rowNum) -> new ParticipantKeyValue(
                getOrDefault(rs, "study_id", defaultStudyId),
                getOrDefault(rs, "participant_id", defaultParticipantId),
                rs.getString("key"),
                (Map<String, Object>) MapperUtils.readValue(rs.getString("value"), Map.class),
                DbUtils.readSet(rs, "observation_group_ids", Integer.class)
        );
    }

    private static RowMapper<ParticipantKeyValue> getRecordRowMapper(Long defaultStudyId, Integer defaultParticipantId, String defaultKey) {
        return (rs, rowNum) -> new ParticipantKeyValue(
                getOrDefault(rs, "study_id", defaultStudyId),
                getOrDefault(rs, "participant_id", defaultParticipantId),
                getOrDefault(rs, "key", defaultKey),
                (Map<String, Object>) MapperUtils.readValue(rs.getString("value"), Map.class),
                DbUtils.readSet(rs, "observation_group_ids", Integer.class)
        );
    }

    private static RowMapper<ParticipantKeyValue> getRecordRowMapper() {
        return (rs, rowNum) -> new ParticipantKeyValue(
                rs.getLong("study_id"),
                rs.getInt("participant_id"),
                rs.getString("key"),
                (Map<String, Object>) MapperUtils.readValue(rs.getString("value"), Map.class),
                DbUtils.readSet(rs, "observation_group_ids", Integer.class)
        );
    }

    private static <T> T getOrDefault(ResultSet rs, String columnName, T defaultValue) {
        try {
            rs.findColumn(columnName);
            T value = rs.getObject(columnName, (Class<T>) defaultValue.getClass());
            return rs.wasNull() ? defaultValue : value;
        } catch (SQLException e) {
            return defaultValue;
        }
    }
}
