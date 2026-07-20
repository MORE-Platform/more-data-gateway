package io.redlink.more.data.model;

import java.time.Instant;

public record ObservationDataHealth (
    Long studyId,
    Integer observationId,
    Integer participantId,
    Instant start,
    Instant end,
    DataHealth health
){ }
