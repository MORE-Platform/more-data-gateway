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
import java.util.List;

public class RecurrenceRule {
    private String freq;
    private Instant until;
    private Integer count;
    private Integer interval;
    private List<String> byDay;
    private Integer byMonth;
    private Integer byMonthDay;
    private Integer bySetPos;

    public RecurrenceRule setFreq(String freq) {
        this.freq = freq;
        return this;
    }

    public String getFreq() {
        return freq;
    }

    public Instant getUntil() {
        return until;
    }

    public RecurrenceRule setUntil(Instant until) {
        this.until = until;
        return this;
    }

    public Integer getCount() {
        return count;
    }

    public RecurrenceRule setCount(Integer count) {
        this.count = count;
        return this;

    }

    public Integer getInterval() {
        return interval;
    }

    public RecurrenceRule setInterval(Integer interval) {
        this.interval = interval;
        return this;
    }

    public List<String> getByDay() {
        return byDay;
    }

    public RecurrenceRule setByDay(List<String> byDay) {
        this.byDay = byDay;
        return this;
    }

    public Integer getByMonth() {
        return byMonth;
    }

    public RecurrenceRule setByMonth(Integer byMonth) {
        this.byMonth = byMonth;
        return this;
    }

    public Integer getByMonthDay() {
        return byMonthDay;
    }

    public RecurrenceRule setByMonthDay(Integer byMonthDay) {
        this.byMonthDay = byMonthDay;
        return this;
    }

    public Integer getBySetPos() {
        return bySetPos;
    }

    public RecurrenceRule setBySetPos(Integer bySetPos) {
        this.bySetPos = bySetPos;
        return this;
    }
}

