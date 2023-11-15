package io.redlink.more.data.model;

import io.redlink.more.data.model.scheduler.ScheduleEvent;

import java.time.Instant;

public record Observation(
         int observationId,
         String title,
         String type,
         String participantInfo,
         Object properties,
         ScheduleEvent observationSchedule,
         Instant created,
         Instant modified,
         boolean hidden,
         boolean noSchedule
) {
    public Observation withProperties(Object properties) {
        return new Observation(
                observationId, title, type, participantInfo, properties, observationSchedule, created, modified, hidden, noSchedule
        );
    }
}
