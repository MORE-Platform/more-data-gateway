/*
 * Copyright LBI-DHP and/or licensed to LBI-DHP under one or more
 * contributor license agreements (LBI-DHP: Ludwig Boltzmann Institute
 * for Digital Health and Prevention -- A research institute of the
 * Ludwig Boltzmann Gesellschaft, Oesterreichische Vereinigung zur
 * Foerderung der wissenschaftlichen Forschung).
 * Licensed under the Elastic License 2.0.
 */
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
