package io.redlink.more.data.model;

public enum DataType {
    HEARTRATE("hr"),
    ACTIVITY("activity_end"),
    SLEEP("sleep_end"),
    DAILY_STEPS("daily_steps"),
    EPOCH_STEPS("epoch_steps"),
    BLOOD_PRESSURE("blood_pressure");

    public final String dataType;

    DataType(String dataType) {
        this.dataType = dataType;
    }
}
