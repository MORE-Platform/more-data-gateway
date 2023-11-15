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
import java.util.List;

public record ParticipantConsent(
        boolean accepted,
        String deviceId,
        String consentMd5,
        Instant consentTimestamp,
        List<ObservationConsent> observationConsents
) {
    public record ObservationConsent(
            int observationId,
            Instant consentTimestamp
    ) {
    }
}
