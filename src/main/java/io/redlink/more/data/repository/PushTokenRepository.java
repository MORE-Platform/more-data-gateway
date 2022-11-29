/*
 * Copyright (c) 2022 Redlink GmbH.
 */
package io.redlink.more.data.repository;

import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class PushTokenRepository {

    private static final String STORE_TOKEN =
            "INSERT INTO push_notifications_token(study_id, participant_id, service, token) " +
            "VALUES (:studyId, :participantId, :service, :token) " +
            "ON CONFLICT (study_id, participant_id) DO UPDATE SET service = excluded.service, token = excluded.token, updated = now()";
    private static final String CLEAR_TOKEN =
            "DELETE FROM push_notifications_token WHERE study_id = :studyId AND participant_id = :participantId";

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PushTokenRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void storeToken(long studyId, int participantId, String serviceType, String token) {
        jdbcTemplate.update(STORE_TOKEN, Map.of(
                "studyId", studyId,
                "participantId", participantId,
                "service", serviceType,
                "token", token
        ));
    }

    public void clearToken(long studyId, int participantId) {
        jdbcTemplate.update(CLEAR_TOKEN, Map.of(
                "studyId", studyId,
                "participantId", participantId
        ));
    }
}
