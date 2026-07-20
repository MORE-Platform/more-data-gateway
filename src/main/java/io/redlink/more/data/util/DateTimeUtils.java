package io.redlink.more.data.util;

import org.apache.commons.lang3.Range;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DateTimeUtils {
    public static OffsetDateTime offsetDateTimeFromEpochSeconds(long epochSecond, int offset) {
        Instant unixTimestamp = Instant.ofEpochSecond(epochSecond);
        ZoneOffset zoneOffset = ZoneOffset.ofTotalSeconds(offset);
        return unixTimestamp.atOffset(zoneOffset);
    }

    public static Instant toInstantOrNull(Object value) {
        if (value == null) return null;
        if (value instanceof Instant i) return i;

        if (value instanceof CharSequence cs) {
            String s = cs.toString().trim();
            if (s.isEmpty()) {
                return null;
            }
            return Instant.parse(s);
        }

        if (value instanceof Number n) {
            long v = n.longValue();
            // Heuristic: treat "big" values as epoch millis, smaller as epoch seconds
            return (v >= 1_000_000_000_000L) ? Instant.ofEpochMilli(v) : Instant.ofEpochSecond(v);
        }

        throw new IllegalArgumentException("Unsupported time value type for startTime/endTime: " + value.getClass().getName());
    }

    public static Set<Range<Instant>> mergeRanges(Set<Range<Instant>> ranges) {
        if (ranges == null || ranges.isEmpty()) return ranges;
        List<Range<Instant>> sorted = ranges.stream()
                .sorted(Comparator.comparing(Range::getMinimum))
                .toList();
        List<Range<Instant>> merged = new ArrayList<>();
        Range<Instant> current = sorted.get(0);
        Instant min = current.getMinimum();
        for (int i = 1; i < sorted.size(); i++) {
            var next = sorted.get(i);
            if (current.isOverlappedBy(next) || !current.getMaximum().isBefore(next.getMinimum())) {
                Instant newMax = current.getMaximum().isAfter(next.getMaximum()) ? current.getMaximum() : next.getMaximum();
                current = Range.of(min, newMax);
            } else {
                merged.add(current);
                current = next;
            }
        }
        merged.add(current);
        return new HashSet<>(merged);
    }

    public static Instant parseInstant(String source) {
        if (source == null || source.isBlank()) {
            return null;
        }

        ZoneOffset systemOffset = ZoneId.systemDefault()
                .getRules()
                .getOffset(LocalDateTime.now());

        return parseInstantWithOffset(source, systemOffset);
    }

    /**
     * Parses an Instant from a string representation, considering a specific timezone offset.
     *
     * @param source     The string representation of the Instant.
     * @param zoneOffset The timezone offset.
     * @return The parsed Instant or null if the source is null or blank.
     */
    public static Instant parseInstantWithOffset(String source, ZoneOffset zoneOffset) {
        if (source == null || source.isBlank()) {
            return null;
        }

        try {
            return Instant.parse(source);
        } catch (DateTimeParseException e) {
            try {
                return LocalDate.parse(source)
                        .atStartOfDay()
                        .atOffset(zoneOffset)
                        .toInstant();
            } catch (DateTimeParseException e2) {
                try {
                    // Handle format: "yyyy-MM-dd HH:mm:ss"
                    return LocalDateTime.parse(source, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                            .atOffset(zoneOffset)
                            .toInstant();
                } catch (DateTimeParseException e3) {
                    throw new IllegalArgumentException("Invalid date format: " + source, e);
                }
            }
        }
    }
}
