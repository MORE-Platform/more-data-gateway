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

    // TODO: switch to dedicated table view (auth_routing_info)
    private static final String GET_AUTH_ROUTING_INFO =
            "SELECT ac.*, pt.study_group_id " +
            "FROM api_credentials ac" +
            "    INNER JOIN participants pt " +
            "        ON (ac.study_id = pt.study_id and ac.participant_id = pt.participant_id) " +
            "WHERE api_id = :api_id";

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
                        readOptionalInt(rs, "study_group_id")
                ));
    }
}
