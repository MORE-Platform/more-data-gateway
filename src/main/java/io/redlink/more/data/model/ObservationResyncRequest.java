package io.redlink.more.data.model;

/**
 * An operator-triggered request to re-collect the data of one observation for one participant.
 * The row in {@code observation_resync_requests} is the request - it is deleted once the data was synced.
 */
public record ObservationResyncRequest(
        long studyId,
        int participantId,
        int observationId,
        String observationType
) {
}
