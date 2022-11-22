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
            "SELECT api_credentials.*, sg.study_group_id " +
            "FROM api_credentials LEFT OUTER JOIN study_groups sg ON (api_credentials.study_id = sg.study_id) " +
            "WHERE api_id = :api_id";

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public GatewayUserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
    }

    public Optional<GatewayUserDetails> findByApiId(String apiId, Set<String> roles) {
        return Optional.ofNullable(
                jdbcTemplate.queryForObject(
                GET_AUTH_ROUTING_INFO,
                Map.of("api_id", apiId),
                (rs, rowNum) -> readUserDetails(rs, roles)
        ));
    }

    private static GatewayUserDetails readUserDetails(ResultSet rs, Set<String> roles) throws SQLException {
        return new GatewayUserDetails(
                rs.getString("api_id"),
                rs.getString("api_secret"),
                roles,
                new RoutingInfo(
                        rs.getLong("study_id"),
                        rs.getInt("participant_id"),
                        readOptionalInt(rs, "study_group_id")
                ));
    }
}
