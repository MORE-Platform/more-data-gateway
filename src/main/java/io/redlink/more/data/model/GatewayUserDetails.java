/*
 * Copyright LBI-DHP and/or licensed to LBI-DHP under one or more
 * contributor license agreements (LBI-DHP: Ludwig Boltzmann Institute
 * for Digital Health and Prevention -- A research institute of the
 * Ludwig Boltzmann Gesellschaft, Oesterreichische Vereinigung zur
 * Foerderung der wissenschaftlichen Forschung).
 * Licensed under the Elastic License 2.0.
 */
package io.redlink.more.data.model;

import java.util.Objects;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Set;
import java.util.stream.Collectors;

public class GatewayUserDetails extends User {

    private final RoutingInfo routingInfo;

    public GatewayUserDetails(String username, String password, Set<String> roles, RoutingInfo routingInfo) {
        super(username, password, roles.stream().map(SimpleGrantedAuthority::new).collect(Collectors.toSet()));
        this.routingInfo = routingInfo;
    }

    public RoutingInfo getRoutingInfo() {
        return routingInfo;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        GatewayUserDetails that = (GatewayUserDetails) o;
        return Objects.equals(routingInfo, that.routingInfo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), routingInfo);
    }
}
