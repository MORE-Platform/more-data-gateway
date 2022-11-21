package io.redlink.more.data.repository;

import io.redlink.more.data.model.RoutingInfo;
import io.redlink.more.data.model.GatewayUserDetails;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Set;

@Service
public class GatewayUserRepository {

    private final JdbcTemplate jdbcTemplate;

    public GatewayUserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<GatewayUserDetails> findByApiId(String apiId, Set<String> roles) {
        return this.jdbcTemplate.queryForObject(
                "select * from gateway_user_details where api_id = ?",
                (rs, rowNum) -> {
                    GatewayUserDetails userDetails = new GatewayUserDetails(
                            rs.getString("api_id"),
                            rs.getString("api_key"),
                            roles,
                            new RoutingInfo(
                                    rs.getString("study_id"),
                                    rs.getString("participant_id")
                            ));
                    return Optional.of(userDetails);
                },
                apiId
        );
    }
}
