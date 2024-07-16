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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class DataTransformer {

    private DataTransformer() {}

    public static List<DataPoint> createDataPoints(DataBulkDTO bulk) {
        final Instant recordingTime = Instant.now();
        return bulk.getDataPoints().stream()
                .map(dp -> createDataPoint(dp, recordingTime))
                .toList();
    }

    public static DataPoint createDataPoint(ObservationDataDTO dataPoint, Instant recordingTime) {
        Instant dateTime = dataPoint.getTimestamp();
        return new DataPoint(
                dataPoint.getDataId(),
                dataPoint.getObservationId(),
                dataPoint.getObservationType(),
                dataPoint.getObservationType(),
                recordingTime,
                dateTime,
                dataPoint.getDataValue());
    }

    public static List<DataPoint> createDataPoints(EndpointDataBulkDTO bulk, ApiRoutingInfo routingInfo, Integer observationId) {
        final Instant recordingTime = Instant.now();
        return bulk.getDataPoints().stream()
                .map(dp -> createDataPoint(dp, routingInfo, recordingTime, observationId))
                .toList();
    }

    public static DataPoint createDataPoint(ExternalDataDTO dataPoint, ApiRoutingInfo routingInfo, Instant recordingTime, Integer observationId) {
        Instant dateTime = dataPoint.getTimestamp();
        return new DataPoint(
                UUID.randomUUID().toString(),
                observationId.toString(),
                routingInfo.observationType(),
                routingInfo.observationType(),
                recordingTime,
                dateTime,
                dataPoint.getDataValue());
    }
}
