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
