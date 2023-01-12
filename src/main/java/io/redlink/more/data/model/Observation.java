package io.redlink.more.data.model;

import java.time.Instant;

public record Observation(
         int observationId,
         String title,
         String type,
         String participantInfo,
         Object properties,
         Event observationSchedule,
         Instant created,
         Instant modified
) {
}
