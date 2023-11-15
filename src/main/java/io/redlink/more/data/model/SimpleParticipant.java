package io.redlink.more.data.model;

import java.time.LocalDateTime;

public record SimpleParticipant(
        int id,
        String alias,
        LocalDateTime start
) {
}
