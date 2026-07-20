package io.redlink.more.data.model;

import java.io.Serializable;
import java.time.Instant;

public record CompletedData(
        String observationId,
        Instant scheduleStart,
        Instant scheduleEnd
) implements Serializable {
    public static CompletedData fromActiveObservation(ActiveObservation activeObservation) {
        return new CompletedData(activeObservation.observationId(), activeObservation.scheduleStart(), activeObservation.scheduleEnd());
    }
}
