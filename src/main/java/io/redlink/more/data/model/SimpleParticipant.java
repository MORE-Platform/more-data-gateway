package io.redlink.more.data.model;

import java.time.Instant;

public record SimpleParticipant(
        int id,
        String alias,
        Instant start,
        Instant end
) {
}
