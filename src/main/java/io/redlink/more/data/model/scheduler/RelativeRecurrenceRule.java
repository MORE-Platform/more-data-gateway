/*
 * Copyright LBI-DHP and/or licensed to LBI-DHP under one or more
 * contributor license agreements (LBI-DHP: Ludwig Boltzmann Institute
 * for Digital Health and Prevention -- A research institute of the
 * Ludwig Boltzmann Gesellschaft, Oesterreichische Vereinigung zur
 * Foerderung der wissenschaftlichen Forschung).
 * Licensed under the Apache 2.0 license (see https://www.apache.org/licenses/LICENSE-2.0).
 */
package io.redlink.more.data.model.scheduler;

public class RelativeRecurrenceRule {

    private Duration frequency;

    private Duration endAfter;

    public RelativeRecurrenceRule() {
    }

    public Duration getFrequency() {
        return frequency;
    }

    public RelativeRecurrenceRule setFrequency(Duration frequency) {
        this.frequency = frequency;
        return this;
    }

    public Duration getEndAfter() {
        return endAfter;
    }

    public RelativeRecurrenceRule setEndAfter(Duration endAfter) {
        this.endAfter = endAfter;
        return this;
    }
}
