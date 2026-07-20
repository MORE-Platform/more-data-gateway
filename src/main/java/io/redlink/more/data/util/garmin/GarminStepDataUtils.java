package io.redlink.more.data.util.garmin;

import io.redlink.more.data.custom.model.GarminDataPoint;
import io.redlink.more.data.model.garmin.transformation.GarminStepData;
import io.redlink.more.data.model.garmin.transformation.GarminTimeData;
import io.redlink.more.data.util.MapperUtils;

import java.time.Instant;

public class GarminStepDataUtils {
    public static GarminTimeData<GarminStepData> getStepData(Instant endDateTime, GarminDataPoint garminDataPoint) {
        GarminStepData stepData = MapperUtils.convertValue(garminDataPoint, GarminStepData.class);
        return new GarminTimeData<>(endDateTime, stepData);
    }
}
