package io.redlink.more.data.model;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Set;
import java.util.stream.Collectors;

public class GatewayUserDetails extends User {

    private final RoutingInfo elastic;

    public GatewayUserDetails(String username, String password, Set<String> roles, RoutingInfo elastic) {
        super(username, password, roles.stream().map(SimpleGrantedAuthority::new).collect(Collectors.toSet()));
        this.elastic = elastic;
    }

    public RoutingInfo getElastic() {
        return elastic;
    }
}
