package io.redlink.more.data.service.observations;

import io.redlink.more.data.model.CallbackResult;
import io.redlink.more.data.model.Observation;
import io.redlink.more.data.model.RoutingInfo;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

public interface ObservationComponent {
    String getObservationType();

    Optional<String> produceUrl(Observation observation, RoutingInfo routingInfo, Instant scheduleStart, Instant scheduleEnd);

    /**
     * Checks if the callback parameters are sufficient to determine the completion of the observation.
     */
    boolean necessaryCallbackParameters(Map<String, String> parameters);

    /**
     * Processes the callback from a completed observation.
     *
     * @param parameters The callback request parameters.
     * @return An optional callback result containing the updated routing information and the observation ID.
     */
    Optional<CallbackResult> processCallback(Map<String, String> parameters);
}
