package io.redlink.more.data.model;

import java.io.Serializable;
import java.time.Instant;

public record ActiveObservation(
        String observationId,
        Instant scheduleStart,
        Instant scheduleEnd
) implements Serializable {

}
