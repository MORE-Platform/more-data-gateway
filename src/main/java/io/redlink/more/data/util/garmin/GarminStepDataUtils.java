/*
 * Copyright LBI-DHP and/or licensed to LBI-DHP under one or more
 * contributor license agreements (LBI-DHP: Ludwig Boltzmann Institute
 * for Digital Health and Prevention -- A research institute of the
 * Ludwig Boltzmann Gesellschaft, Oesterreichische Vereinigung zur
 * Foerderung der wissenschaftlichen Forschung).
 * Licensed under the Apache 2.0 license (see https://www.apache.org/licenses/LICENSE-2.0).
 */
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
