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
