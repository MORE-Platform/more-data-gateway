package io.redlink.more.data.model;

import io.redlink.more.data.model.scheduler.Duration;
import org.apache.commons.lang3.tuple.Pair;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

public class StudyDurationInfo{
    private LocalDate endDate;
    private LocalDate startDate;
    private Duration duration;
    private final List<Pair<Integer, Duration>> groupDurations = new ArrayList<>();

    public LocalDate getEndDate() {
        return endDate;
    }

    public StudyDurationInfo setEndDate(LocalDate endDate) {
        this.endDate = endDate;
        return this;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public StudyDurationInfo setStartDate(LocalDate startDate) {
        this.startDate = startDate;
        return this;
    }

    public Duration getDuration() {
        return duration;
    }

    public StudyDurationInfo setDuration(Duration duration) {
        this.duration = duration;
        return this;
    }

    public List<Pair<Integer, Duration>> getGroupDurations() {
        return groupDurations;
    }

    public StudyDurationInfo addGroupDuration(Pair<Integer, Duration> gd) {
        this.groupDurations.add(gd);
        return this;
    }

    public Duration getDurationFor(Integer group) {
        if(group == null) {
            return getDurationFallback();
        } else {
            return this.groupDurations.stream()
                .filter(gd -> gd.getLeft().equals(group))
                .findFirst()
                .map(Pair::getRight)
                .orElse(getDurationFallback());
        }
    }

    private Duration getDurationFallback() {
        if(this.duration != null) {
            return duration;
        } else {
            return new Duration().setUnit(Duration.Unit.DAY).setValue((int) startDate.until(endDate, ChronoUnit.DAYS));
        }
    }
}
