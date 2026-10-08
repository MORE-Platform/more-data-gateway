package io.redlink.more.data.service;

import io.redlink.more.data.model.Observation;
import io.redlink.more.data.model.ObservationResyncRequest;
import io.redlink.more.data.model.RoutingInfo;
import io.redlink.more.data.repository.ObservationResyncRepository;
import io.redlink.more.data.repository.StudyRepository;
import io.redlink.more.data.service.observations.limesurvey.LimeSurveyComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Picks up the resync requests in {@code observation_resync_requests} (written by the Studymanager and by
 * opening a survey in the participant portal) and re-collects the observation data, every
 * {@link ObservationResyncRequest#interval()} until its {@link ObservationResyncRequest#end()}.
 * A request that collected data is marked synced, so the Studymanager runs the data health check and deletes it.
 * A request past its end without data is deleted.
 */
@Service
public class ObservationResyncService {
    private static final Logger LOG = LoggerFactory.getLogger(ObservationResyncService.class);

    private final Map<ObservationResyncRequest, Instant> retryAfter = new ConcurrentHashMap<>();

    private final ObservationResyncRepository resyncRepository;
    private final StudyRepository studyRepository;
    private final LimeSurveyComponent limeSurveyComponent;

    public ObservationResyncService(ObservationResyncRepository resyncRepository,
                                    StudyRepository studyRepository,
                                    LimeSurveyComponent limeSurveyComponent) {
        this.resyncRepository = resyncRepository;
        this.studyRepository = studyRepository;
        this.limeSurveyComponent = limeSurveyComponent;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void processPendingRequests() {
        LOG.info("Processing pending observation resync requests");
        final List<ObservationResyncRequest> pending;
        try {
            pending = resyncRepository.listPending();
        } catch (DataAccessException e) {
            LOG.error("Could not read pending observation resync requests: {}", e.toString());
            return;
        }

        retryAfter.keySet().retainAll(pending);
        if (pending.isEmpty()) {
            return;
        }

        LOG.info("Found {} pending observation resync requests", pending.size());

        Instant now = Instant.now();
        for (ObservationResyncRequest request : pending) {
            Instant retryAt = retryAfter.get(request);
            if (retryAt != null && retryAt.isAfter(now)) {
                continue;
            }
            boolean expired = !now.isBefore(request.end());
            LimeSurveyComponent.ResyncResult result;
            try {
                result = resync(request);
            } catch (RuntimeException e) {
                LOG.error("Error resyncing {}", request, e);
                result = LimeSurveyComponent.ResyncResult.NOTHING;
            }
            try {
                // before its end only a newly submitted answer counts, the token is reused across schedules
                if (result.stored() > 0 && (result.submittedSince() || expired)) {
                    resyncRepository.markSynced(request);
                    retryAfter.remove(request);
                    LOG.info("Resynced {}, waiting for the data health check", request);
                } else if (expired) {
                    resyncRepository.delete(request);
                    retryAfter.remove(request);
                    LOG.info("Resync {} ended without new data and was removed", request);
                } else {
                    retryAfter.put(request, now.plus(request.interval().period()));
                    LOG.info("No new data for {} yet, retrying in {} minutes", request, request.interval().period().toMinutes());
                }
            } catch (DataAccessException e) {
                LOG.error("Could not update resync request {}", request, e);
            }
        }
    }

    private LimeSurveyComponent.ResyncResult resync(ObservationResyncRequest request) {
        if (!limeSurveyComponent.getObservationType().equals(request.observationType())) {
            // Only LimeSurvey is resyncable for now; anything else is removed after its end.
            LOG.warn("Ignoring resync request for unsupported observation type `{}`: {}", request.observationType(), request);
            return LimeSurveyComponent.ResyncResult.NOTHING;
        }

        Optional<RoutingInfo> routingInfo = studyRepository.getRoutingInfo(request.studyId(), request.participantId());
        if (routingInfo.isEmpty()) {
            LOG.warn("No routing info for resync request {}", request);
            return LimeSurveyComponent.ResyncResult.NOTHING;
        }

        Map<String, Object> properties = studyRepository
                .filterObservations(request.studyId(), request.participantId(),
                        observation -> observation.observationId() == request.observationId())
                .stream()
                .findFirst()
                .map(Observation::properties)
                .filter(Map.class::isInstance)
                .map(props -> (Map<String, Object>) props)
                .orElse(null);
        if (properties == null) {
            LOG.warn("No observation properties for resync request {}", request);
            return LimeSurveyComponent.ResyncResult.NOTHING;
        }

        return limeSurveyComponent.resync(routingInfo.get(), request.observationId(), properties, request.start());
    }
}
