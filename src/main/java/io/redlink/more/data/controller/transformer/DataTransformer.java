/*
 * Copyright (c) 2022 Redlink GmbH.
 */
package io.redlink.more.data.controller.transformer;

import io.redlink.more.data.api.app.v1.model.DataBulkDTO;
import io.redlink.more.data.api.app.v1.model.EndpointDataBulkDTO;
import io.redlink.more.data.api.app.v1.model.ExternalDataDTO;
import io.redlink.more.data.api.app.v1.model.ObservationDataDTO;
import io.redlink.more.data.model.ApiRoutingInfo;
import io.redlink.more.data.model.DataPoint;
import io.redlink.more.data.model.RoutingInfo;

import java.time.Instant;
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
                BaseTransformers.toInstant(dataPoint.getTimestamp()),
                dataPoint.getDataValue());
    }

    public static List<DataPoint> createDataPoints(EndpointDataBulkDTO bulk, ApiRoutingInfo routingInfo) {
        final Instant recordingTime = Instant.now();
        return bulk.getDataPoints().stream()
                .map(dp -> createDataPoint(dp, routingInfo, recordingTime))
                .toList();
    }

    public static DataPoint createDataPoint(ExternalDataDTO dataPoint, ApiRoutingInfo routingInfo, Instant recordingTime) {
        return new DataPoint(
                dataPoint.getDataId(),
                String.valueOf(routingInfo.observationId()),
                routingInfo.observationType(),
                routingInfo.observationType(),
                recordingTime,
                BaseTransformers.toInstant(dataPoint.getTimestamp()),
                dataPoint.getDataValue());
    }
}
