/*
 * Copyright LBI-DHP and/or licensed to LBI-DHP under one or more
 * contributor license agreements (LBI-DHP: Ludwig Boltzmann Institute
 * for Digital Health and Prevention -- A research institute of the
 * Ludwig Boltzmann Gesellschaft, Oesterreichische Vereinigung zur
 * Foerderung der wissenschaftlichen Forschung).
 * Licensed under the Apache 2.0 license (see https://www.apache.org/licenses/LICENSE-2.0).
 */
package io.redlink.more.data.model.garmin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public enum GarminSummaryType {
    DAILIES,
    EPOCHS,
    STRESSDETAILS,
    PULSEOX,
    SLEEPS,
    HRV,
    BLOODPRESSURES;

    public final String label;

    private static final Logger LOG = LoggerFactory.getLogger(GarminSummaryType.class);

    GarminSummaryType() {
        label = name().toLowerCase();
    }

    public static GarminSummaryType fromLabel(String label) {
        try {
            return GarminSummaryType.valueOf(label.toUpperCase());
        } catch (IllegalArgumentException e) {
            LOG.warn("Invalid GarminSummaryType: {}", label);
            return null;
        }
    }
}
