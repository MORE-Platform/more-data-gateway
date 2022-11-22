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
