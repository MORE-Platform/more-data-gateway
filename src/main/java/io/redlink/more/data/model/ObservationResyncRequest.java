package io.redlink.more.data.model;

import java.time.Instant;

/**
 * A request to re-collect the data of one observation for one participant, retried every {@code interval}
 * from {@code start} until {@code end}. The row in {@code observation_resync_requests} is the request - once the
 * data was collected it is marked synced (the Studymanager then runs the data health check and deletes it),
 * after its end it is deleted.
 */
public record ObservationResyncRequest(
        long studyId,
        int participantId,
        int observationId,
        String observationType,
        ResyncInterval interval,
        Instant start,
        Instant end
) {
}
