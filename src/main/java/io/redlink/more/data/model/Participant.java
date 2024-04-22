package io.redlink.more.data.model;

import java.time.Instant;
import java.util.OptionalInt;

public record Participant(
        int id,
        String alias,
        String status,
        OptionalInt studyGroupId,
        String studyGroupTitle,
        Instant start
) {}
