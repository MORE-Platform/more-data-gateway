/*
 * Copyright LBI-DHP and/or licensed to LBI-DHP under one or more
 * contributor license agreements (LBI-DHP: Ludwig Boltzmann Institute
 * for Digital Health and Prevention -- A research institute of the
 * Ludwig Boltzmann Gesellschaft, Oesterreichische Vereinigung zur
 * Foerderung der wissenschaftlichen Forschung).
 * Licensed under the Elastic License 2.0.
 */
package io.redlink.more.data.model;

import io.redlink.more.data.model.scheduler.ScheduleEvent;

import java.time.Instant;

public record Observation(
         int observationId,
         Integer groupId,
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
                observationId, groupId, title, type, participantInfo, properties, observationSchedule, created, modified, hidden, noSchedule
        );
    }
}
