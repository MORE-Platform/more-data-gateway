package io.redlink.more.data.model;

import java.time.Instant;
import java.util.Map;

/**
 * A request to re-collect the data of one observation for one participant, retried every {@code interval}
 * from {@code start} until {@code end}. The row in {@code observation_resync_requests} is the request - once the
 * data was collected it is marked synced (the Studymanager then runs the data health check and deletes it),
 * after its end it is deleted. {@code properties} carries what the Studymanager's validation asks for, e.g. a
 * {@link #MISSING_SUBMIT_DATE} backfill.
 */
public record ObservationResyncRequest(
        long studyId,
        int participantId,
        int observationId,
        String observationType,
        ResyncInterval interval,
        Instant start,
        Instant end,
        Map<String, Object> properties
) {
    /** Property (boolean): the stored LimeSurvey answer has no submitdate yet */
    public static final String MISSING_SUBMIT_DATE = "missingSubmitDate";
    /** Property: the {@code data_id} (LimeSurvey response id) of the stored answer without submitdate */
    public static final String DATA_ID = "dataId";
}
