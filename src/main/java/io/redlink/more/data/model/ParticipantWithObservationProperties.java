/*
 * Copyright LBI-DHP and/or licensed to LBI-DHP under one or more
 * contributor license agreements (LBI-DHP: Ludwig Boltzmann Institute
 * for Digital Health and Prevention -- A research institute of the
 * Ludwig Boltzmann Gesellschaft, Oesterreichische Vereinigung zur
 * Foerderung der wissenschaftlichen Forschung).
 * Licensed under the Apache 2.0 license (see https://www.apache.org/licenses/LICENSE-2.0).
 */
package io.redlink.more.data.model;

import java.util.Map;

public record ParticipantWithObservationProperties(
        Integer participantId,
        Long studyId,
        Integer observationId,
        Map<String, Object> properties
) {
    public ParticipantWithObservationProperties updateProperties(Map<String, Object> properties) {
        return new ParticipantWithObservationProperties(this.participantId, this.studyId, this.observationId, properties);
    }
}
