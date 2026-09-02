/*
 * Copyright LBI-DHP and/or licensed to LBI-DHP under one or more
 * contributor license agreements (LBI-DHP: Ludwig Boltzmann Institute
 * for Digital Health and Prevention -- A research institute of the
 * Ludwig Boltzmann Gesellschaft, Oesterreichische Vereinigung zur
 * Foerderung der wissenschaftlichen Forschung).
 * Licensed under the Apache 2.0 license (see https://www.apache.org/licenses/LICENSE-2.0).
 */
package io.redlink.more.data.transformers.garmin;

import io.redlink.more.data.controller.transformer.StudyTransformer;
import io.redlink.more.data.custom.model.GarminDataPoint;
import io.redlink.more.data.model.DataPoint;
import io.redlink.more.data.model.DataType;
import io.redlink.more.data.model.Observation;
import io.redlink.more.data.model.ParticipantObservationSeed;
import io.redlink.more.data.model.ParticipantMilestone;
import io.redlink.more.data.model.garmin.GarminSummaryType;
import io.redlink.more.data.model.garmin.transformation.GarminTimeData;
import io.redlink.more.data.repository.StudyRepository;
import io.redlink.more.data.service.milestone.ParticipantMilestoneService;
import io.redlink.more.data.util.DateTimeUtils;
import io.redlink.more.data.util.SchedulerUtils;
import org.apache.commons.lang3.Range;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static io.redlink.more.data.util.StringUtils.sha256;

public abstract class AbstractGarminTransformer {
    @Autowired
    private StudyRepository studyRepository;

    @Autowired
    private ParticipantMilestoneService participantMilestoneService;

    public abstract GarminSummaryType getSupportedType();

    public abstract String getObservationType();

    protected abstract List<DataPoint> transformToDataPoint(List<Observation> observations, GarminDataPoint garminDataPoint);

    protected abstract List<DataPoint> filterDataPointByTimeRange(List<Range<Instant>> validTimeRanges, List<DataPoint> dataBulk);

    public List<DataPoint> transform(List<Observation> observations, GarminDataPoint garminDataPoint, Instant participantStart, Instant participantEnd) {
        return transform(observations, garminDataPoint, participantStart, participantEnd, null, null);
    }

    public List<DataPoint> transform(List<Observation> observations, GarminDataPoint garminDataPoint, Instant participantStart, Instant participantEnd, Long studyId, Integer participantId) {
        var participantObservationProperties = studyRepository
                .getAllParticpantObservationProperties(studyId, participantId).stream().map(StudyTransformer::toParticipantObservationSeed).toList();
        var validObservations = filterObservations(participantObservationProperties, observations, garminDataPoint, participantStart, participantEnd, studyId, participantId);
        if (validObservations.isEmpty()) {
            return Collections.emptyList();
        }
        var range = observations
                .stream()
                .flatMap(observation -> {
                    var seed = participantObservationProperties
                            .stream()
                            .filter(p -> p.observationId().equals(observation.observationId()))
                            .findFirst()
                            .orElse(null);
                    return resolveSchedules(seed, observation, participantStart, participantEnd, studyId, participantId).stream();
                })
                .toList();
        var dataPoints = transformToDataPoint(validObservations, garminDataPoint);
        return filterDataPointByTimeRange(range, dataPoints);
    }

    protected List<DataPoint> transformGarminTimeDataToDataPoint(List<Observation> observations, String summaryId, DataType dataType, GarminTimeData<?> data) {
        if (data != null) {
            return observations.stream().map(observation ->
                            new DataPoint(
                                    uniqueSummaryId(summaryId, dataType, data.timestamp()),
                                    String.valueOf(observation.observationId()),
                                    observation.type(),
                                    dataType.name(),
                                    Instant.now(),
                                    data.timestamp(),
                                    data.dataToMap(dataType.dataType, summaryId)
                            )
                    )
                    .toList();
        }
        return Collections.emptyList();
    }

    protected OffsetDateTime recordingTimestamp(GarminDataPoint garminDataPoint) {
        return DateTimeUtils.offsetDateTimeFromEpochSeconds(garminDataPoint.getStartTimeInSeconds(), garminDataPoint.getStartTimeOffsetInSeconds());
    }

    protected Range<Instant> getGarminDataPointTimeRange(GarminDataPoint garminDataPoint) {
        return Range.of(recordingTimestamp(garminDataPoint).toInstant(), calculateEndInstant(garminDataPoint));
    }

    protected Instant calculateEndInstant(GarminDataPoint garminDataPoint) {
        var startTime = recordingTimestamp(garminDataPoint).toInstant();
        long totalDuration = garminDataPoint.getDurationInSeconds();
        return startTime.plusSeconds(totalDuration);
    }

    private List<Observation> filterObservations(List<ParticipantObservationSeed> seeds, List<Observation> observations, GarminDataPoint garminDataPoint, Instant participantStart, Instant participantEnd, Long studyId, Integer participantId) {
        Range<Instant> garminDataTimeRange = getGarminDataPointTimeRange(garminDataPoint);
        return observations
                .stream()
                .filter(observation -> {
                    var seed = seeds
                            .stream()
                            .filter(p -> p.observationId().equals(observation.observationId()))
                            .findFirst()
                            .orElse(null);
                    var instantRanges = resolveSchedules(seed, observation, participantStart, participantEnd, studyId, participantId);
                    return instantRanges.stream().anyMatch(range -> range.isOverlappedBy(garminDataTimeRange));
                })
                .toList();
    }

    private List<Range<Instant>> resolveSchedules(ParticipantObservationSeed seed, Observation observation, Instant participantStart, Instant participantEnd, Long studyId, Integer participantId) {
        if (observation.milestoneId() == null) {
            return SchedulerUtils.parseToObservationSchedules(seed, observation.observationSchedule(), participantStart, participantEnd, false);
        }
        if (studyId == null || participantId == null) {
            return Collections.emptyList();
        }
        Optional<ParticipantMilestone> milestone = participantMilestoneService.findParticipantMilestone(studyId, participantId, observation.milestoneId());
        if (milestone.isEmpty()) {
            return Collections.emptyList();
        }
        return SchedulerUtils.parseToObservationSchedules(seed, observation.observationSchedule(), milestone.get().dateTime(), participantEnd, true);
    }

    private String uniqueSummaryId(String summaryId, DataType dataType, Instant timestamp) {
        String base = summaryId + "_" + dataType.name() + "_" + timestamp.toEpochMilli();
        String uuid = UUID.randomUUID().toString();

        return sha256(base + uuid);
    }
}
