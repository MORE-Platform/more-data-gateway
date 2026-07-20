package io.redlink.more.data.store.observationCallback;

import io.redlink.more.data.model.ActiveObservation;
import io.redlink.more.data.model.CompletedData;
import io.redlink.more.data.model.RoutingInfo;

import java.net.URI;
import java.util.List;
import java.util.Optional;

public interface ObservationCallbackStore {
    void saveRedirect(RoutingInfo routingInfo, ActiveObservation activeObservation, String redirect);

    Optional<URI> pullRedirect(RoutingInfo routingInfo, int observationId);

    boolean isCompleted(RoutingInfo routingInfo, ActiveObservation activeObservation);

    void markCompleted(RoutingInfo routingInfo, ActiveObservation activeObservation);

    List<CompletedData> getCompletedData(RoutingInfo routingInfo);
}