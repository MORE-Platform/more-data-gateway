/*
 * Copyright LBI-DHP and/or licensed to LBI-DHP under one or more
 * contributor license agreements (LBI-DHP: Ludwig Boltzmann Institute
 * for Digital Health and Prevention -- A research institute of the
 * Ludwig Boltzmann Gesellschaft, Oesterreichische Vereinigung zur
 * Foerderung der wissenschaftlichen Forschung).
 * Licensed under the Elastic License 2.0.
 */
package io.redlink.more.data.properties;

import java.net.URI;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "more")
public record MoreProperties(
        GatewayProperties gateway
) {

    public MoreProperties {
        gateway = Objects.requireNonNullElse(gateway, new GatewayProperties(null));
    }

    public record GatewayProperties(
            URI baseUrl
    ) {}

}
