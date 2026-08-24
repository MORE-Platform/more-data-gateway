/*
 * Copyright LBI-DHP and/or licensed to LBI-DHP under one or more
 * contributor license agreements (LBI-DHP: Ludwig Boltzmann Institute
 * for Digital Health and Prevention -- A research institute of the
 * Ludwig Boltzmann Gesellschaft, Oesterreichische Vereinigung zur
 * Foerderung der wissenschaftlichen Forschung).
 * Licensed under the Apache 2.0 license (see https://www.apache.org/licenses/LICENSE-2.0).
 */
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
