/*
 * Copyright (c) 2022 Redlink GmbH.
 */
package io.redlink.more.data.controller.transformer;

import io.redlink.more.data.api.app.v1.model.DataBulkDTO;
import io.redlink.more.data.api.app.v1.model.ObservationDataDTO;
import io.redlink.more.data.model.DataPoint;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

public final class DataTransformer {

    private DataTransformer() {}

    public static List<DataPoint> createDataPoints(DataBulkDTO bulk) {
        final Instant recordingTime = Instant.now();
        return bulk.getDataPoints().stream()
                .map(dp -> createDataPoint(dp, recordingTime))
                .toList();
    }

    public static DataPoint createDataPoint(ObservationDataDTO dataPoint, Instant recordingTime) {
        return new DataPoint(
                dataPoint.getDataId(),
                dataPoint.getObservationId(),
                dataPoint.getObservationType(),
                dataPoint.getObservationType(),
                recordingTime,
                toInstant(dataPoint.getTimestamp()),
                dataPoint.getDataValue());
    }

    public static Instant toInstant(OffsetDateTime dateTime) {
        if (dateTime == null)
            return null;
        return dateTime.toInstant();
    }
}
