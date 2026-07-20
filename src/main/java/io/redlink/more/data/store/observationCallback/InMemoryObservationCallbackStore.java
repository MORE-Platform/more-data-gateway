package io.redlink.more.data.store.observationCallback;

import io.redlink.more.data.model.ActiveObservation;
import io.redlink.more.data.model.CompletedData;
import io.redlink.more.data.model.RoutingInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnMissingBean(
        value = ObservationCallbackStore.class,
        ignored = InMemoryObservationCallbackStore.class
)
public class InMemoryObservationCallbackStore implements ObservationCallbackStore {
    private static final Logger LOG = LoggerFactory.getLogger(InMemoryObservationCallbackStore.class);
    private final Map<String, Map<ActiveObservation, String>> externalRedirects = new ConcurrentHashMap<>();
    private final Map<String, List<CompletedData>> completedDataMap = new ConcurrentHashMap<>();

    @Override
    public void saveRedirect(RoutingInfo routingInfo, ActiveObservation activeObservation, String redirect) {
        this.externalRedirects.computeIfAbsent(routingInfo.participantRef(), k -> Collections.synchronizedMap(new LinkedHashMap<>()))
                .put(activeObservation, redirect);
    }

    @Override
    public Optional<URI> pullRedirect(RoutingInfo routingInfo, int observationId) {
        if (routingInfo == null) {
            LOG.warn("RoutingInfo is null, cannot pull redirect for observationId: {}", observationId);
            throw new IllegalArgumentException("RoutingInfo may not be null for getting redirects!");
        }
        var observationWithRedirect = externalRedirects.getOrDefault(routingInfo.participantRef(), Collections.emptyMap());
        LOG.debug("Found {} external redirects for participant {}", observationWithRedirect.size(), routingInfo.participantRef());
        var lastActiveObservation = observationWithRedirect.keySet().stream()
                .filter(ao -> Integer.parseInt(ao.observationId()) == observationId)
                .findFirst();
        if (lastActiveObservation.isPresent()) {
            LOG.debug("Found redirect for observationId: {}", observationId);
            String externalRedirect = observationWithRedirect.remove(lastActiveObservation.get());
            markCompleted(routingInfo, lastActiveObservation.get());
            if (observationWithRedirect.isEmpty()) {
                externalRedirects.remove(routingInfo.participantRef());
            } else {
                externalRedirects.put(routingInfo.participantRef(), observationWithRedirect);
            }
            if (externalRedirect != null && !externalRedirect.isBlank()) {
                LOG.debug("Redirecting to {}", externalRedirect);
                return Optional.of(URI.create(externalRedirect));
            }
        }
        LOG.warn("No redirect found for observationId: {}", observationId);
        return Optional.empty();
    }

    @Override
    public boolean isCompleted(RoutingInfo routingInfo, ActiveObservation activeObservation) {
        return completedDataMap.getOrDefault(routingInfo.participantRef(), Collections.emptyList())
                .contains(CompletedData.fromActiveObservation(activeObservation));
    }

    @Override
    public void markCompleted(RoutingInfo routingInfo, ActiveObservation activeObservation) {
        completedDataMap.computeIfAbsent(routingInfo.participantRef(), k -> Collections.synchronizedList(new LinkedList<>()))
                .add(CompletedData.fromActiveObservation(activeObservation));
    }

    @Override
    public List<CompletedData> getCompletedData(RoutingInfo routingInfo) {
        return completedDataMap.getOrDefault(routingInfo.participantRef(), Collections.emptyList());
    }
}
