package io.redlink.more.data.service;

import io.redlink.more.data.exception.ForbiddenException;
import io.redlink.more.data.exception.NotFoundException;
import io.redlink.more.data.model.ActiveObservation;
import io.redlink.more.data.model.CallbackData;
import io.redlink.more.data.model.CompletedData;
import io.redlink.more.data.model.Observation;
import io.redlink.more.data.model.ParticipantObservationSeed;
import io.redlink.more.data.model.RoutingInfo;
import io.redlink.more.data.model.SimpleParticipant;
import io.redlink.more.data.model.Study;
import io.redlink.more.data.service.observations.ObservationComponent;
import io.redlink.more.data.store.observationCallback.ObservationCallbackStore;
import io.redlink.more.data.util.SchedulerUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.Range;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ObservationExecutionService {
    private static final Logger LOG = LoggerFactory.getLogger(ObservationExecutionService.class);

    private final StudyService studyService;
    private final ObservationCallbackStore callbackStore;
    private final Map<String, ObservationComponent> observationComponents;

    public ObservationExecutionService(
            StudyService studyService,
            ObservationCallbackStore callbackStore,
            List<ObservationComponent> observationComponents) {
        this.studyService = studyService;
        this.callbackStore = callbackStore;
        this.observationComponents = observationComponents.stream()
                .collect(Collectors.toMap(ObservationComponent::getObservationType, c -> c));
    }

    public Optional<URI> executeObservation(String observationId, Instant scheduleStart, Instant scheduleEnd, RoutingInfo routingInfo, String redirect) {
        if (observationId.isBlank() || !StringUtils.isNumeric(observationId)) {
            throw new NotFoundException("Observation not found!");
        }
        ActiveObservation activeObservation = new ActiveObservation(observationId, scheduleStart, scheduleEnd);
        if (callbackStore.isCompleted(routingInfo, activeObservation)) {
            LOG.debug("Observation {} already done, redirecting...", CompletedData.fromActiveObservation(activeObservation));
            if (redirect != null) {
                return Optional.of(UriComponentsBuilder.fromUriString(redirect)
                        .replaceQueryParam("status", HttpStatus.CONFLICT.value())
                        .build().toUri());
            }
            return Optional.empty();
        }

        if (redirect != null && !redirect.isBlank()) {
            callbackStore.saveRedirect(routingInfo, activeObservation, redirect);
        }

        LOG.info("Starting observation {} for routinginfo {}", activeObservation, routingInfo);

        Optional<Pair<Study, List<ParticipantObservationSeed>>> studyResult = studyService.getStudy(routingInfo);
        if (studyResult.isEmpty()) {
            throw new NotFoundException("Study not found for " + routingInfo);
        }

        Study study = studyResult.get().getLeft();
        List<ParticipantObservationSeed> seeds = studyResult.get().getRight();

        String state = study.studyState();
        if (!"active".equalsIgnoreCase(state) && !"preview".equalsIgnoreCase(state)) {
            throw new ForbiddenException("Study is not active or in preview");
        }

        int observationIdAsInt = Integer.parseInt(observationId);

        Optional<Observation> studyObservation = study.observations().stream()
                .filter(o -> o.observationId() == observationIdAsInt)
                .findFirst();

        if (studyObservation.isEmpty()) {
            throw new NotFoundException("Observation " + observationId + " not found in study " + study.studyId());
        }
        Observation observation = studyObservation.get();

        ObservationComponent component = observationComponents.get(observation.type());
        if (component == null) {
            throw new NotFoundException("Component for observation type " + observation.type() + " not found");
        }

        ParticipantObservationSeed seed = seeds.stream()
                .filter(s -> String.valueOf(s.observationId()).equals(observationId))
                .findFirst()
                .orElse(null);

        ZoneId zoneId = ZoneId.systemDefault();
        Instant start = study.startDate() != null ? study.startDate().atStartOfDay(zoneId).toInstant() : scheduleStart.atZone(zoneId).toLocalDate().atStartOfDay(zoneId).toInstant();
        Instant end = study.endDate() != null ? study.endDate().plusDays(1).atStartOfDay(zoneId).toInstant() : scheduleEnd.atZone(zoneId).toLocalDate().plusDays(1).atStartOfDay(zoneId).toInstant();

        List<Range<Instant>> validRanges = SchedulerUtils.parseToObservationSchedules(seed, observation.observationSchedule(), start, end);
        boolean isValidSchedule = validRanges.stream()
                .anyMatch(r -> (r.getMinimum().equals(scheduleStart) && r.getMaximum().equals(scheduleEnd))
                        || r.contains(scheduleStart) && r.contains(scheduleEnd));

        if (!isValidSchedule) {
            LOG.error("Provided schedule is not valid for observation {}: scheduleStart: {}; scheduleEnd: {}; validRanges: {}", observationId, scheduleStart, scheduleEnd, validRanges);
            throw new ForbiddenException("Provided schedule is not valid for observation " + observationId);
        }

        var uri = URI.create(component.produceUrl(observation, routingInfo, scheduleStart, scheduleEnd)
                .orElseThrow(() -> new NotFoundException("Could not produce URL for observation " + observationId)));
        LOG.info("Opening url `{}` for routinginfo {} and observation schedule {}", uri, routingInfo, activeObservation);
        return Optional.of(uri);
    }

    public Optional<URI> processCallback(Map<String, String> parsedParameters) {
        LOG.info("process callback for params: {}", parsedParameters);
        CallbackData callbackData = toCallbackData(parsedParameters);
        LOG.info("processCallback: {}", callbackData);
        Optional<Pair<RoutingInfo, Integer>> cbResult = Optional.empty();

        ObservationComponent component = observationComponents.get(callbackData.getObservation().type());
        if (component != null) {
            LOG.info("mapped to study {}, observation {},  component: {}", callbackData.studyId(), callbackData.observationId(), component.getClass().getSimpleName());
            cbResult = component.processCallback(callbackData.getRoutingInfo(), callbackData.getObservation(), callbackData.getParams());
        } else {
            LOG.warn("ObservationComponent for Observation-type: {} not found (study {}, observation: {})", callbackData.getObservation().type(), callbackData.studyId(), callbackData.observationId());
        }

        if (cbResult.isPresent()) {
            return callbackStore.pullRedirect(cbResult.get().getLeft(), cbResult.get().getRight());
        } else {
            LOG.warn("No callback result generated for: {}", callbackData);
        }

        return Optional.empty();
    }

    public List<CompletedData> getCompletedData(RoutingInfo routingInfo) {
        return callbackStore.getCompletedData(routingInfo);
    }

    /**
     * Validates the data provided by the callback parameters
     * @param parsedParameters the parameters
     * @return the parsed and validated callback data
     */
    //NOTE: This should be replaced by a key based solution, where the backend creates a callback key and the gateway
    //      uses this key to lookup studyId, observationId and participantId from the database. This would avoid
    //      sharing internal Ids with external applications. In this case this Method would use the key and a
    //      callback repository to retrieve the CallbackData from the key!
    private CallbackData toCallbackData(Map<String, String> parsedParameters) {
        final Map<String, String> params = new HashMap<>(parsedParameters); //do not modify parsed parameter map
        long studyId = consumeParams(params,"studyId", "studyid", "study_id", "study-id")
                .map(Long::parseLong)
                .orElseThrow(() -> new IllegalArgumentException("Missing required Parameter studyId"));
        int participantId = consumeParams(params, "participantId", "participantid", "participant_id", "participant-id")
                .map(Integer::parseInt)
                .orElseThrow(() -> new IllegalArgumentException("Missing required Parameter participantId"));
        int observationId = consumeParams(params, "observationId", "observationid", "observation_id", "observation-id")
                .map(Integer::parseInt)
                .orElseThrow(() -> new IllegalArgumentException("Missing required Parameter participantId"));

        Study study = studyService.getStudy(studyId)
                .orElseThrow(() -> new IllegalArgumentException("Study with id: " + studyId + " not found"));
        Observation observation = study.observations().stream()
                .filter(o -> o.observationId() ==observationId)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Observation with id: " + observationId + " not found in Study " + studyId));
        SimpleParticipant participant = studyService.getParticipant(studyId, participantId)
                .orElseThrow(() -> new IllegalArgumentException("Participant with id: " + participantId + " not found in Study " + studyId));
        return new CallbackData(study, observation, participant, params);
    }

    /**
     * Removes all params for the parsed keys and returns the first value or empty if none is present
     * @param parameters the map with the parameters
     * @param keys the keys to consume
     * @return the first value or empty if none
     */
    private Optional<String> consumeParams(Map<String, String> parameters, String... keys) {
        String value = null;
        for(String key : keys) {
            String v = parameters.remove(key);
            if(value == null) {
                value = v;
            }
        }
        return Optional.ofNullable(value);
    }

}
