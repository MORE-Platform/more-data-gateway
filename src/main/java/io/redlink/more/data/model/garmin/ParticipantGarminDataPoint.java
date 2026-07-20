package io.redlink.more.data.model.garmin;

import io.redlink.more.data.custom.model.GarminDataPoint;
import io.redlink.more.data.model.ParticipantKeyValue;

import java.util.List;

public record ParticipantGarminDataPoint(
        ParticipantKeyValue participantKeyValue,
        List<GarminDataPoint> garminDataPoints
) {
}
