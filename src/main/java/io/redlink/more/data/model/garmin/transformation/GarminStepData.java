package io.redlink.more.data.model.garmin.transformation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * @param steps            Number of steps per Daily or Epoch Summary
 * @param stepsGoal        Goal of steps count. Only present in Daily Summaries
 * @param distanceInMeters Distance travelled in meters per Daily or Epoch Summary
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GarminStepData(
        Integer steps,
        Integer stepsGoal,
        Double distanceInMeters
) {
}
