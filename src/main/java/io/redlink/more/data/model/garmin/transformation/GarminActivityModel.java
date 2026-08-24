/*
 * Copyright LBI-DHP and/or licensed to LBI-DHP under one or more
 * contributor license agreements (LBI-DHP: Ludwig Boltzmann Institute
 * for Digital Health and Prevention -- A research institute of the
 * Ludwig Boltzmann Gesellschaft, Oesterreichische Vereinigung zur
 * Foerderung der wissenschaftlichen Forschung).
 * Licensed under the Apache 2.0 license (see https://www.apache.org/licenses/LICENSE-2.0).
 */
package io.redlink.more.data.model.garmin.transformation;

import io.redlink.more.data.custom.model.GarminDataPoint;


/**
 * This model is to hold the extracted garmin data, before transforming them into a Datapoint
 *
 * @param activityType Represents an activity type enum (WALKING, RUNNING, SEDENTARY, WHEELCHAIR_PUSHING, SLEEP, GENERIC)
 * @param met          Metabolic equivalent of task (MET)
 * @param intensity    Represents and intesity type enum (HIGHLY_ACTIVE, ACTIVE, SEDENTARY)
 */
public record GarminActivityModel(
        GarminDataPoint.ActivityTypeEnum activityType,
        Double met,
        GarminDataPoint.IntensityEnum intensity,
        Long activeTimeInSeconds,
        Double meanMotionIntensity,
        Double maxMotionIntensity
) {
}
