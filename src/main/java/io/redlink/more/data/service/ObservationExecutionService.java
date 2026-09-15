package io.redlink.more.data.service;

import io.redlink.more.data.exception.ForbiddenException;
import io.redlink.more.data.exception.NotFoundException;
import io.redlink.more.data.model.ActiveObservation;
import io.redlink.more.data.model.CallbackResult;
import io.redlink.more.data.model.CompletedData;
import io.redlink.more.data.model.Observation;
import io.redlink.more.data.model.ParticipantMilestone;
import io.redlink.more.data.model.ParticipantObservationSeed;
import io.redlink.more.data.model.RoutingInfo;
import io.redlink.more.data.model.Study;
import io.redlink.more.data.service.milestone.ParticipantMilestoneService;
import io.redlink.more.data.service.observations.ObservationComponent;
import io.redlink.more.data.store.observationCallback.ObservationCallbackStore;
import io.redlink.more.data.util.SchedulerUtils;
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
import java.util.Collections;
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
    private final ParticipantMilestoneService participantMilestoneService;

    public ObservationExecutionService(
            StudyService studyService,
            ObservationCallbackStore callbackStore,
            List<ObservationComponent> observationComponents,
            ParticipantMilestoneService participantMilestoneService) {
        this.studyService = studyService;
        this.callbackStore = callbackStore;
        this.observationComponents = observationComponents.stream()
                .collect(Collectors.toMap(ObservationComponent::getObservationType, c -> c));
        this.participantMilestoneService = participantMilestoneService;
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
        Instant end = study.endDate() != null ? study.endDate().plusDays(1).atStartOfDay(zoneId).toInstant() : scheduleEnd.atZone(zoneId).toLocalDate().plusDays(1).atStartOfDay(zoneId).toInstant();

        List<Range<Instant>> validRanges;
        if (observation.milestoneId() != null) {
            Optional<ParticipantMilestone> milestone = participantMilestoneService.findParticipantMilestone(
                    routingInfo.studyId(), routingInfo.participantId(), observation.milestoneId());
            validRanges = milestone.isEmpty() ? Collections.emptyList() : SchedulerUtils.parseToObservationSchedules(
                    seed, observation.observationSchedule(), milestone.get().dateTime(), end, true);
        } else {
            Instant start = study.startDate() != null ? study.startDate().atStartOfDay(zoneId).toInstant() : scheduleStart.atZone(zoneId).toLocalDate().atStartOfDay(zoneId).toInstant();
            validRanges = SchedulerUtils.parseToObservationSchedules(seed, observation.observationSchedule(), start, end, false);
        }
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

    public Optional<URI> processCallback(Map<String, String> parameters) {
        LOG.info("process callback for params: {}", parameters);
        Optional<CallbackResult> cbResult = Optional.empty();
        for (ObservationComponent component : observationComponents.values()) {
            if (component.necessaryCallbackParameters(parameters)) {
                var result = component.processCallback(parameters);
                if (result.isPresent()) {
                    LOG.info("mapped to {} with result: {}", component.getClass().getSimpleName(), result);
                    cbResult = result;
                    break;
                }
            } else {
                LOG.info("Necessary parameters not provided for component {}", component.getClass().getSimpleName());
            }
        }

        if (cbResult.isPresent()) {
            return callbackStore.pullRedirect(cbResult.get().routingInfo(), cbResult.get().observationId());
        }

        LOG.warn("No callback result generated for callback result: {}, params: {}", cbResult, parameters);
        return Optional.empty();
    }

    public List<CompletedData> getCompletedData(RoutingInfo routingInfo) {
        return callbackStore.getCompletedData(routingInfo);
    }
}
