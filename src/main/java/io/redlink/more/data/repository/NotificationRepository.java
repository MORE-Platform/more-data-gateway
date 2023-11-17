/*
 * Copyright LBI-DHP and/or licensed to LBI-DHP under one or more
 * contributor license agreements (LBI-DHP: Ludwig Boltzmann Institute
 * for Digital Health and Prevention -- A research institute of the
 * Ludwig Boltzmann Gesellschaft, Oesterreichische Vereinigung zur
 * Foerderung der wissenschaftlichen Forschung).
 * Licensed under the Elastic License 2.0.
 */
package io.redlink.more.data.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.redlink.more.data.api.app.v1.model.PushNotificationDTO;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Component
public class NotificationRepository {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String LIST_AND_DELETE_NOTIFICATIONS =
            "DELETE FROM notifications " +
                    "WHERE study_id = :study_id AND participant_id = :participant_id RETURNING *";
    private static final String DELETE_NOTIFICATION = "DELETE FROM notifications " +
            "WHERE study_id = :study_id AND participant_id = :participant_id AND msg_id = :msg_id";

    private final NamedParameterJdbcTemplate namedTemplate;

    public NotificationRepository(NamedParameterJdbcTemplate namedTemplate) {
        this.namedTemplate = namedTemplate;
    }

    public List<PushNotificationDTO> listAndDeleteFor(Long studyId, Integer participantId) {
        return this.namedTemplate.query(LIST_AND_DELETE_NOTIFICATIONS, Map.of(
                "study_id", studyId, "participant_id", participantId
        ), getRowMapper());
    }

    public int delete(Long studyId, Integer participantId, String msgId) {
        return this.namedTemplate.update(DELETE_NOTIFICATION, Map.of(
                "study_id", studyId, "participant_id", participantId, "msg_id", msgId
        ));
    }

    private static RowMapper<PushNotificationDTO> getRowMapper() {
        return (rs, rowNum) -> {
            var result = new PushNotificationDTO()
                    .msgId(rs.getString("msg_id"))
                    .timestamp(getTimestamp(rs))
                    .type(PushNotificationDTO.TypeEnum.fromValue(rs.getString("type")));
            var data = toJson(rs.getString("data"));
            switch (result.getType()) {
                case TEXT -> result
                        .body(valueFor("body", data))
                        .title(valueFor("title", data))
                        .deepLink(valueFor("deepLink", data));
                default -> result.data(data);
            }
            return result;
        };
    }

    private static OffsetDateTime getTimestamp(ResultSet rs) {
        try {
            return Optional.ofNullable(rs.getTimestamp("timestamp"))
                    .map(d -> d.toInstant().atOffset(ZoneOffset.UTC))
                    .orElse(null);
        } catch (SQLException e) {
            return null;
        }
    }

    private static Map toJson(String s) {
        try {
            return MAPPER.readValue(s, Map.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private static String valueFor(String key, Map map) {
        return Optional.ofNullable(map.get(key)).map(Object::toString).orElse(null);
    }
}
