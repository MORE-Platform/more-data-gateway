/*
 * Copyright LBI-DHP and/or licensed to LBI-DHP under one or more
 * contributor license agreements (LBI-DHP: Ludwig Boltzmann Institute
 * for Digital Health and Prevention -- A research institute of the
 * Ludwig Boltzmann Gesellschaft, Oesterreichische Vereinigung zur
 * Foerderung der wissenschaftlichen Forschung).
 * Licensed under the Elastic License 2.0.
 */
package io.redlink.more.data.repository;

import io.redlink.more.data.model.GatewayUserDetails;
import io.redlink.more.data.model.RoutingInfo;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import static io.redlink.more.data.repository.DbUtils.readOptionalInt;

@Service
public class GatewayUserRepository {

    private static final String GET_AUTH_ROUTING_INFO =
            "SELECT * FROM auth_routing_info WHERE api_id = :api_id";

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public GatewayUserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
    }

    public Optional<GatewayUserDetails> findByApiId(String apiId, Set<String> roles) {
        try (var stream = jdbcTemplate.queryForStream(
                GET_AUTH_ROUTING_INFO,
                Map.of("api_id", apiId),
                (rs, rowNum) -> readUserDetails(rs, roles)
        )) {
            return stream.findFirst();
        }
    }

    private static GatewayUserDetails readUserDetails(ResultSet rs, Set<String> roles) throws SQLException {
        return new GatewayUserDetails(
                rs.getString("api_id"),
                rs.getString("api_secret"),
                roles,
                new RoutingInfo(
                        rs.getLong("study_id"),
                        rs.getInt("participant_id"),
                        readOptionalInt(rs, "study_group_id"),
                        rs.getBoolean("study_is_active"),
                        true // TODO: This could be read from the db-view, but should always be true
                ));
    }
}
