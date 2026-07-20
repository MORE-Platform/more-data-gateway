package io.redlink.more.data.service.garmin;

import io.redlink.more.data.model.DataPoint;
import io.redlink.more.data.model.Observation;
import io.redlink.more.data.model.ParticipantKeyValue;
import io.redlink.more.data.model.RoutingInfo;
import io.redlink.more.data.model.garmin.GarminSummaryType;
import io.redlink.more.data.model.garmin.ParticipantGarminDataPoint;
import io.redlink.more.data.repository.StudyRepository;
import io.redlink.more.data.transformers.garmin.AbstractGarminTransformer;
import org.apache.commons.lang3.Range;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class GarminDataTransformationService {
    private static final Logger LOG = LoggerFactory.getLogger(GarminDataTransformationService.class);
    private final StudyRepository studyRepository;
    private final Map<GarminSummaryType, List<AbstractGarminTransformer>> transformersByType;

    public GarminDataTransformationService(
            StudyRepository studyRepository,
            List<AbstractGarminTransformer> transformers
    ) {
        this.studyRepository = studyRepository;
        this.transformersByType = transformers.stream()
                .collect(Collectors.groupingBy(AbstractGarminTransformer::getSupportedType));
    }

    public Map<RoutingInfo, List<DataPoint>> transformData(
            GarminSummaryType summaryType,
            List<ParticipantGarminDataPoint> participantGarminDataPoints) {

        LOG.debug("Transforming Garmin summary type {} to data points", summaryType);
        List<AbstractGarminTransformer> transformers = transformersByType.get(summaryType);

        if (transformers == null || transformers.isEmpty()) {
            LOG.debug("No transformer found for type {}", summaryType);
            return Collections.emptyMap();
        }

        return participantGarminDataPoints
                .stream()
                .map(participantGarminDataPoint -> {
                    RoutingInfo routingInfo = participantKeyValueToRoutingInfo(participantGarminDataPoint.participantKeyValue());

                    Range<Instant> participantTimeRange = getParticipantTimeRange(routingInfo);
                    if (participantTimeRange == null) {
                        return Map.entry(routingInfo, Collections.<DataPoint>emptyList());
                    }

                    Set<String> observationTypes = transformers.stream().map(AbstractGarminTransformer::getObservationType).collect(Collectors.toSet());
                    List<Observation> observations = participantObservations(routingInfo, observationTypes);

                    List<DataPoint> dataPoints = participantGarminDataPoint.garminDataPoints()
                            .parallelStream()
                            .flatMap(data -> transformers
                                    .stream()
                                    .flatMap(transformer ->
                                            transformer.transform(
                                                            filterObservationByType(observations, transformer.getObservationType()),
                                                            data,
                                                            participantTimeRange.getMinimum(),
                                                            participantTimeRange.getMaximum(),
                                                            routingInfo.studyId(),
                                                            routingInfo.participantId())
                                                    .stream()
                                    )
                            )
                            .toList();

                    return Map.entry(
                            routingInfo,
                            dataPoints
                    );
                })
                .filter(entry -> !entry.getValue().isEmpty())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private Range<Instant> getParticipantTimeRange(RoutingInfo routingInfo) {
        var simpleParticipant = studyRepository.findParticipant(routingInfo);
        if (simpleParticipant.isEmpty()) {
            LOG.warn("No participant found for {}", routingInfo);
            return null;
        }
        var participantStart = simpleParticipant.get().start();
        var participantEnd = simpleParticipant.get().end();
        if (participantStart.isAfter(participantEnd)) {
            LOG.warn("Participant {} has start {} after end {}!", simpleParticipant.get(), participantStart, participantEnd);
            return null;
        }
        return Range.of(participantStart, participantEnd);
    }

    private List<Observation> participantObservations(RoutingInfo routingInfo, Set<String> observationTypes) {
        return studyRepository.getObservationsByTypes(
                routingInfo,
                observationTypes,
                routingInfo.observationGroupIds()
        );
    }

    private List<Observation> filterObservationByType(List<Observation> observations, String observationTypes) {
        return observations.stream().filter(observation -> observation.type().equals(observationTypes)).toList();
    }


    private RoutingInfo participantKeyValueToRoutingInfo(ParticipantKeyValue participantKeyValue) {
        return new RoutingInfo(
                participantKeyValue.studyId(),
                participantKeyValue.participantId(),
                OptionalInt.empty(),
                participantKeyValue.observationGroupIds(),
                true,
                true
        );
    }
}