package io.redlink.more.data.model;

public record Observation(
         int observationId,
         String title,
         String type,
         String participantInfo,
         Object properties,
         Event observationSchedule
) {
}
