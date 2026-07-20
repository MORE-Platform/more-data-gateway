package io.redlink.more.data.model.garmin.transformation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Represents a blood pressure record in the Garmin ecosystem.
 * This record encapsulates systolic and diastolic blood pressure readings,
 * pulse information, and the source from which the data was obtained.
 * <p>
 * The {@link SourceType} enum defines the possible origins of the measurement,
 * which can either be entered manually or recorded by a device.
 *
 * @param systolic   The systolic blood pressure value measured in mmHg.
 * @param diastolic  The diastolic blood pressure value measured in mmHg.
 * @param pulse      The pulse rate measured in beats per minute (BPM).
 * @param sourceType The origin of the blood pressure record (manual entry or device).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GarminBloodPressure(
        Integer systolic,
        Integer diastolic,
        Integer pulse,
        SourceType sourceType
) {
    public enum SourceType {
        MANUAL,
        DEVICE
    }
}
