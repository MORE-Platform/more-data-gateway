package io.redlink.more.data.model;

import java.time.Duration;
import java.util.Locale;

/**
 * How often a pending {@link ObservationResyncRequest} is retried. Stored lowercase in
 * {@code observation_resync_requests.resync_interval}.
 */
public enum ResyncInterval {
    URGENT(Duration.ofMinutes(1)),
    HIGH(Duration.ofMinutes(5)),
    NORMAL(Duration.ofMinutes(10)),
    LOW(Duration.ofMinutes(30));

    private final Duration period;

    ResyncInterval(Duration period) {
        this.period = period;
    }

    public Duration period() {
        return period;
    }

    public String dbValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static ResyncInterval fromDb(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }
}
