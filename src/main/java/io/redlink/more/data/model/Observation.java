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
         Instant modified,
         boolean hidden
) {
    public Observation withProperties(Object properties) {
        return new Observation(
                observationId, title, type, participantInfo, properties, observationSchedule, created, modified, hidden
        );
    }
}
