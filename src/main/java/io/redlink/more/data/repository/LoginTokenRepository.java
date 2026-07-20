/*
 * Copyright LBI-DHP and/or licensed to LBI-DHP under one or more
 * contributor license agreements (LBI-DHP: Ludwig Boltzmann Institute
 * for Digital Health and Prevention -- A research institute of the
 * Ludwig Boltzmann Gesellschaft, Österreichische Vereinigung zur
 * Förderung der wissenschaftlichen Forschung).
 * Licensed under the Elastic License 2.0.
 */
package io.redlink.more.data.repository;

import io.redlink.more.data.model.LoginToken;
import io.redlink.more.data.model.ParticipantApplication;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class LoginTokenRepository {
    private static final String SELECT_BY_CODE_HASH =
            "SELECT * FROM login_tokens WHERE code_hash = ? AND study_id = ? AND application = ? AND participant_id = ?";
    private static final String DELETE_SALT =
            "DELETE FROM salt_tokens WHERE study_id = ? AND participant_id = ?";
    private static final String DELETE_BY_PARTICIPANT =
            "DELETE FROM login_tokens WHERE study_id = ? AND participant_id = ?";

    private final JdbcTemplate template;

    public LoginTokenRepository(JdbcTemplate template) {
        this.template = template;
    }

    public Optional<LoginToken> findByCodeHash(String codeHash, ParticipantApplication application) {
        return template.query(SELECT_BY_CODE_HASH, getRowMapper(), codeHash, application.getStudyId(), application.getApplication(), application.getParticipantId())
                .stream().findFirst();
    }

    public void deleteLoginTokens(Long studyId, Integer participantId) {
        if (participantId == null || studyId == null) {
            return;
        }
        template.update(DELETE_BY_PARTICIPANT, studyId, participantId);
        template.update(DELETE_SALT, studyId, participantId);
    }

    private RowMapper<LoginToken> getRowMapper() {
        return (rs, rowNum) -> new LoginToken()
                .setStudyId(rs.getLong("study_id"))
                .setParticipantId(rs.getInt("participant_id"))
                .setApplication(rs.getString("application"))
                .setCode(rs.getString("code"))
                .setCodeHash(rs.getString("code_hash"));
    }
}
