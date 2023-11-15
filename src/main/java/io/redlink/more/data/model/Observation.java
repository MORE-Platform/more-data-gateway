/*
 * Copyright LBI-DHP and/or licensed to LBI-DHP under one or more
 * contributor license agreements (LBI-DHP: Ludwig Boltzmann Institute
 * for Digital Health and Prevention -- A research institute of the
 * Ludwig Boltzmann Gesellschaft, Oesterreichische Vereinigung zur
 * Foerderung der wissenschaftlichen Forschung).
 * Licensed under the Elastic License 2.0.
 */
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
         boolean hidden,
         boolean noSchedule
) {
    public Observation withProperties(Object properties) {
        return new Observation(
                observationId, title, type, participantInfo, properties, observationSchedule, created, modified, hidden, noSchedule
        );
    }
}
