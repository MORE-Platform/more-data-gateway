package io.redlink.more.data.util;

import io.redlink.more.data.model.DataPoint;
import org.apache.commons.lang3.Range;

import java.time.Instant;

public class DataUtils {
    public static Range<Instant> buildRangeFromDataPoint(DataPoint dp) {
        Instant effectiveDateTime = dp.effectiveDateTime();

        Object startObj = dp.data().getOrDefault("startTime", null);
        Object endObj = dp.data().getOrDefault("endTime", null);

        Instant start = DateTimeUtils.toInstantOrNull(startObj);
        Instant end = DateTimeUtils.toInstantOrNull(endObj);

        // Collect the best possible range boundaries from what we have:
        // - If start/end exist, the base range is [min(start,end), max(start,end)]
        // - If effectiveDateTime lies outside, it extends the range
        // - If effectiveDateTime lies between, range stays start..end (effective is "in between" but Range can only store min/max)
        // - If only one of start/end exists, pair it with effectiveDateTime if available
        // - If only effectiveDateTime exists, range collapses to a single instant
        if (start == null && end == null) {
            if (effectiveDateTime == null) {
                throw new IllegalArgumentException("Cannot build time range: startTime, endTime and effectiveDateTime are all null");
            }
            return Range.of(effectiveDateTime, effectiveDateTime);
        }

        Instant min;
        Instant max;

        if (start != null && end != null) {
            min = start.isBefore(end) ? start : end;
            max = start.isAfter(end) ? start : end;
        } else {
            Instant only = (start != null) ? start : end;
            if (effectiveDateTime == null) {
                return Range.of(only, only);
            }
            min = only.isBefore(effectiveDateTime) ? only : effectiveDateTime;
            max = only.isAfter(effectiveDateTime) ? only : effectiveDateTime;
            return Range.of(min, max);
        }

        if (effectiveDateTime != null) {
            if (effectiveDateTime.isBefore(min)) {
                min = effectiveDateTime;
            } else if (effectiveDateTime.isAfter(max)) {
                max = effectiveDateTime;
            }
        }

        return Range.of(min, max);
    }
}
