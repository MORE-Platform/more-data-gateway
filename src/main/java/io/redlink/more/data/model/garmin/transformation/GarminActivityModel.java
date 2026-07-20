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
