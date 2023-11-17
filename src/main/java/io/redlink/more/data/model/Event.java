/*
 * Copyright LBI-DHP and/or licensed to LBI-DHP under one or more
 * contributor license agreements (LBI-DHP: Ludwig Boltzmann Institute
 * for Digital Health and Prevention -- A research institute of the
 * Ludwig Boltzmann Gesellschaft, Oesterreichische Vereinigung zur
 * Foerderung der wissenschaftlichen Forschung).
 * Licensed under the Elastic License 2.0.
 */
package io.redlink.more.data.model;

import java.time.Instant;

public class Event {
    private Instant dateStart;
    private Instant dateEnd;
    private RecurrenceRule recurrenceRule;

    public Instant getDateStart() {
        return dateStart;
    }

    public Event setDateStart(Instant dateStart) {
        this.dateStart = dateStart;
        return this;
    }

    public Instant getDateEnd() {
        return dateEnd;
    }

    public Event setDateEnd(Instant dateEnd) {
        this.dateEnd = dateEnd;
        return this;
    }

    public RecurrenceRule getRRule() {
        return recurrenceRule;
    }

    public Event setRRule(RecurrenceRule recurrenceRule) {
        this.recurrenceRule = recurrenceRule;
        return this;
    }
}
