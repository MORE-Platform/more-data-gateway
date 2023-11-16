package io.redlink.more.data.model.scheduler;

import java.time.Instant;

public class Interval {
    private Instant start;
    private Instant end;

    public Interval(Instant start, Instant end) {
        this.start = start;
        this.end = end;
    }

    public static Interval from(Event event) {
        return new Interval(event.getDateStart(), event.getDateEnd());
    }

    public Instant getStart() {
        return start;
    }

    public Instant getEnd() {
        return end;
    }
}
