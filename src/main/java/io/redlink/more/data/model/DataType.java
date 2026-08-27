/*
 * Copyright LBI-DHP and/or licensed to LBI-DHP under one or more
 * contributor license agreements (LBI-DHP: Ludwig Boltzmann Institute
 * for Digital Health and Prevention -- A research institute of the
 * Ludwig Boltzmann Gesellschaft, Oesterreichische Vereinigung zur
 * Foerderung der wissenschaftlichen Forschung).
 * Licensed under the Apache 2.0 license (see https://www.apache.org/licenses/LICENSE-2.0).
 */
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
