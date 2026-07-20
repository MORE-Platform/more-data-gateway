/*
 * Copyright LBI-DHP and/or licensed to LBI-DHP under one or more
 * contributor license agreements (LBI-DHP: Ludwig Boltzmann Institute
 * for Digital Health and Prevention -- A research institute of the
 * Ludwig Boltzmann Gesellschaft, Österreichische Vereinigung zur
 * Förderung der wissenschaftlichen Forschung).
 * Licensed under the Elastic License 2.0.
 */
package io.redlink.more.data.service;

import io.redlink.more.data.model.DataHealth;
import io.redlink.more.data.model.ObservationDataHealth;
import io.redlink.more.data.model.ObservationDataState;
import io.redlink.more.data.repository.DataHealthRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

@Service
public class DataHealthService {

    private final DataHealthRepository repository;

    private static final EnumSet<ObservationDataState> COMPLETED_DATA_STATES = EnumSet.of(ObservationDataState.COMPLETE);
    private static final EnumSet<ObservationDataState> ACTIVE_DATA_STATES = EnumSet.complementOf(COMPLETED_DATA_STATES);

    public DataHealthService(DataHealthRepository repository) {
        this.repository = repository;
    }

    /**
     * The latest start time of any {@link OccurredObservation} for the parst studyId
     * @param studyId the study id
     * @param participantId the participantId
     * @return the latest start time or <code>null</code> if no {@link OccurredObservation} is present for the study
     */
    public Instant getLatestStartTime(
            long studyId,
            int participantId,
            Instant startTime,
            Instant endTime) {
        return repository.getLatestStartTime(studyId, participantId, null, null, null);
    }

    /**
     * Stream over all occurred observations for a study. <p>
     * <b>NOTE:</b> DO NOT forget to close the returned stream!!
     * {@link ObservationDataState#COMPLETE})
     * @param studyId the study id
     * @param startTime if present it filters for <code>oo.start &gt;= startTime</code>
     * @param endTime if present it filters for <code>oo.end &lt;= endTime</code>
     * @param includeInvalid if occurred observations marked as having invalid data are included
     * @param includeCompleted if FALSE DataHealh information is only provided for observation with incomplete/missing data
     * @return a stream over all ongoing or occurred observations where data are still missing
     */
    public Stream<ObservationDataHealth> streamDataHealthForStudy(
            long studyId,
            Instant startTime,
            Instant endTime,
            boolean includeInvalid,
            boolean includeCompleted
    ) {
        return repository.listObservationDataHealth(
                studyId, null, null,
                includeInvalid ? null : true,
                includeCompleted ? null : ACTIVE_DATA_STATES,
                startTime,
                endTime);
    }

    /**
     * Stream over all data health data for an observation of a study. <p>
     * <b>NOTE:</b> DO NOT forget to close the returned stream!!
     * {@link ObservationDataState#COMPLETE})
     * @param studyId the study id
     * @param observationId the observation id
     * @param startTime if present it filters for <code>oo.start &gt;= startTime</code>
     * @param endTime if present it filters for <code>oo.end &lt;= endTime</code>
     * @param includeInvalid if occurred observations marked as having invalid data are included
     * @param includeCompleted if FALSE DataHealh information is only provided for observation with incomplete/missing data
     * @return a stream over all ongoing or occurred observations where data are still missing
     */
    public Stream<ObservationDataHealth> streamObservationDataHealthForObservation(
            long studyId,
            int observationId,
            Instant startTime,
            Instant endTime,
            boolean includeInvalid,
            boolean includeCompleted
    ) {
        return repository.listObservationDataHealth(
                studyId, null, observationId,
                includeInvalid ? null : true,
                includeCompleted ? null : ACTIVE_DATA_STATES,
                startTime,
                endTime);
    }

    /**
     * Stream over all occurred observations for a participant of a study.<p>
     * <b>NOTE:</b> DO NOT forget to close the returned stream!!
     * @param studyId the study id
     * @param participantId the participant id
     * @param startTime if present it filters for <code>oo.start &gt;= startTime</code>
     * @param endTime if present it filters for <code>oo.end &lt;= endTime</code>
     * @param includeInvalid if occurred observations marked as having invalid data are included
     * @param includeCompleted if FALSE DataHealh information is only provided for observation with incomplete/missing data
     * @return a stream over all ongoing or occurred observations where data are still missing
     */
    public Stream<ObservationDataHealth> streamObservationDataHealthForParticipant(
            long studyId,
            int participantId,
            Instant startTime,
            Instant endTime,
            boolean includeInvalid,
            boolean includeCompleted
    ) {
        return repository.listObservationDataHealth(
                studyId, participantId, null,
                includeInvalid ? null : true,
                includeCompleted ? null : ACTIVE_DATA_STATES,
                startTime,
                endTime);
    }

    /**
     * Gets all occurred observations for a participant of a study, where data is still missing (not in
     * {@link ObservationDataState#COMPLETE})
     * @param studyId the study id
     * @param participantId the participant id
     * @param observationId the observation id
     * @param includeInvalid if occurred observations marked as having invalid data are included
     * @param observationDataStates the states to include or <code>null</code> to include all
     * @param startTime if present it filters for <code>oo.start &gt;= startTime</code>
     * @param endTime if present it filters for <code>oo.end &lt;= endTime</code>
     * @return a stream over all ongoing or occurred observations where data are still missing
     */
    public List<ObservationDataHealth> listDataHealth(
            long studyId,
            int participantId,
            int observationId,
            boolean includeInvalid,
            Set<ObservationDataState> observationDataStates,
            Instant startTime,
            Instant endTime
    ) {
        try (Stream<ObservationDataHealth> stream = repository.listObservationDataHealth(
                studyId, participantId, observationId,
                includeInvalid ? null : true,
                observationDataStates == null ? EnumSet.allOf(ObservationDataState.class) : observationDataStates,
                startTime,
                endTime)) {
            return stream.toList();
        }
    }

    /**
     * Gets all occurred observations for a participant of a study, where data is still missing (not in
     * {@link ObservationDataState#COMPLETE})
     * @param studyId the study id
     * @param participantId the participant id or <code>null</code> as wildcard
     * @param observationId the observation id  or <code>null</code> as wildcard
     * @param startTime if present it filters for <code>oo.start &gt;= startTime</code>
     * @return the data health of the matching OccurredObservation or {@link DataHealth#MISSING} if not found
     */
    public DataHealth checkDataHealth(
            long studyId,
            int participantId,
            int observationId,
            Instant startTime) {
        return repository.getByIds(studyId, participantId, observationId, startTime)
                .map(ObservationDataHealth::health)
                .orElse(DataHealth.MISSING);
    }
}
