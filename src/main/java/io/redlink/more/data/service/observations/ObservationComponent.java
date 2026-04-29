package io.redlink.more.data.service.observations;

import io.redlink.more.data.model.Observation;
import io.redlink.more.data.model.RoutingInfo;
import io.redlink.more.data.model.Study;
import jakarta.annotation.Nullable;
import org.apache.commons.lang3.tuple.Pair;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

public interface ObservationComponent {
    String getObservationType();

    Optional<String> produceUrl(Observation observation, RoutingInfo routingInfo, Instant scheduleStart, Instant scheduleEnd);

    /**
     * Processes the callback from a completed observation.
     *
     * @param parameters  The callback request parameters.
     * @param routingInfo The routing information for the given callback request. May be null and set using the request parameters.
     * @param observation The observation that was completed. May be null and set using the request parameters.
     * @return An optional pair containing the updated routing information and the observation ID.
     */
    Optional<Pair<RoutingInfo, Integer>> processCallback(RoutingInfo routingInfo, Observation observation, Map<String, String> parameters);
}
