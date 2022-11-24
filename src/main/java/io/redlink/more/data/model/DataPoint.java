package io.redlink.more.data.model;

import java.time.Instant;
import java.util.Map;

public record DataPoint(
        String datapointId,
        String observationId,
        String observationType,
        String dataType,
        Instant serverTime,
        Instant effectiveDateTime,
        Map<String, Object> data
) {

    public DataPoint {
        data = Map.copyOf(data);
    }

}
