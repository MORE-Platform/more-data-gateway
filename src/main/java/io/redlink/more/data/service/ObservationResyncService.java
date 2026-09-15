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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Picks up the resync requests the Studymanager writes to {@code observation_resync_requests} and
 * re-collects the observation data. A request that was synced is deleted, everything else is retried
 * after {@link #RETRY_AFTER}.
 */
@Service
public class ObservationResyncService {
    private static final Logger LOG = LoggerFactory.getLogger(ObservationResyncService.class);
    private static final Duration RETRY_AFTER = Duration.ofMinutes(10);

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
            try {
                if (resync(request)) {
                    resyncRepository.delete(request);
                    retryAfter.remove(request);
                    LOG.info("Resynced {} and removed the request", request);
                } else {
                    retryAfter.put(request, now.plus(RETRY_AFTER));
                    LOG.info("Could not resync {} yet, retrying in {} minutes", request, RETRY_AFTER.toMinutes());
                }
            } catch (RuntimeException e) {
                retryAfter.put(request, now.plus(RETRY_AFTER));
                LOG.error("Error resyncing {}, retrying in {} minutes", request, RETRY_AFTER.toMinutes(), e);
            }
        }
    }

    private boolean resync(ObservationResyncRequest request) {
        if (!limeSurveyComponent.getObservationType().equals(request.observationType())) {
            // Only LimeSurvey is resyncable for now; anything else would be retried forever.
            LOG.warn("Ignoring resync request for unsupported observation type `{}`: {}", request.observationType(), request);
            return false;
        }

        Optional<RoutingInfo> routingInfo = studyRepository.getRoutingInfo(request.studyId(), request.participantId());
        if (routingInfo.isEmpty()) {
            LOG.warn("No routing info for resync request {}", request);
            return false;
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
            return false;
        }

        return limeSurveyComponent.resync(routingInfo.get(), request.observationId(), properties) > 0;
    }
}
