/*
 * Copyright LBI-DHP and/or licensed to LBI-DHP under one or more
 * contributor license agreements (LBI-DHP: Ludwig Boltzmann Institute
 * for Digital Health and Prevention -- A research institute of the
 * Ludwig Boltzmann Gesellschaft, Österreichische Vereinigung zur
 * Förderung der wissenschaftlichen Forschung).
 * Licensed under the Elastic License 2.0.
 */
package io.redlink.more.data.model.scheduler;

/**
 * Schedule of a study-wide observation: it has nothing to configure and runs for the
 * complete study, from the effective start to the effective end.
 * <p>
 * Written by the study-manager, where the observation factory declares itself study-wide.
 * The gateway has no access to those factories, so the stored schedule itself is what
 * carries the signal.
 */
public class StudyWideEvent implements ScheduleEvent {
    public static final String TYPE = "StudyWideEvent";
    private String type;

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public Randomization getRandomization() {
        return null;
    }

    @Override
    public ScheduleEvent setRandomization(Randomization randomization) {
        return this;
    }
}
