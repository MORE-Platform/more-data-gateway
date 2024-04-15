package io.redlink.more.data.model;

import java.time.Instant;

public record Participant(
        int id,
        String alias,
        String status,
        Integer studyGroupId,
        Instant start
) {}
