package io.redlink.more.data.model.scheduler;

import org.apache.commons.lang3.Range;

import java.time.Instant;
import java.util.List;

public class Interval {
    private final Instant start;
    private final Instant end;


    public Interval(Instant start, Instant end) {
        this.start = start;
        this.end = end;
    }

    @Override
    public String toString() {
        return String.format("Interval[start=%s, end=%s]", start, end);
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

    public boolean containsTimestamp(Instant timestamp) {
        return !timestamp.isBefore(start) && !timestamp.isAfter(end);
    }


    public static List<Interval> fromRanges(List<Range<Instant>> ranges) {
        if (ranges == null) {
            throw new IllegalArgumentException("Range list cannot be null");
        }
        return ranges.stream()
                .map(range -> new Interval(range.getMinimum(), range.getMaximum()))
                .toList();
    }
}
