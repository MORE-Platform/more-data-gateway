package io.redlink.more.data.util;

import io.redlink.more.data.model.DataPoint;
import org.apache.commons.lang3.Range;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DataUtilsTest {

    @Test
    void testBuildRangeFromDataPoint_Full() {
        Instant start = Instant.parse("2023-01-01T10:00:00Z");
        Instant end = Instant.parse("2023-01-01T11:00:00Z");
        Instant effective = Instant.parse("2023-01-01T10:30:00Z");

        DataPoint dp = new DataPoint("id", "obsId", "obsType", "dataType", Instant.now(), effective,
                Map.of("startTime", start.toString(), "endTime", end.toString()));

        Range<Instant> range = DataUtils.buildRangeFromDataPoint(dp);
        assertEquals(start, range.getMinimum());
        assertEquals(end, range.getMaximum());
    }

    @Test
    void testBuildRangeFromDataPoint_EffectiveOutside() {
        Instant start = Instant.parse("2023-01-01T10:00:00Z");
        Instant end = Instant.parse("2023-01-01T11:00:00Z");
        Instant effective = Instant.parse("2023-01-01T11:30:00Z");

        DataPoint dp = new DataPoint("id", "obsId", "obsType", "dataType", Instant.now(), effective,
                Map.of("startTime", start.toString(), "endTime", end.toString()));

        Range<Instant> range = DataUtils.buildRangeFromDataPoint(dp);
        assertEquals(start, range.getMinimum());
        assertEquals(effective, range.getMaximum());
    }

    @Test
    void testBuildRangeFromDataPoint_OnlyStartAndEffective() {
        Instant start = Instant.parse("2023-01-01T10:00:00Z");
        Instant effective = Instant.parse("2023-01-01T11:00:00Z");

        DataPoint dp = new DataPoint("id", "obsId", "obsType", "dataType", Instant.now(), effective,
                Map.of("startTime", start.toString()));

        Range<Instant> range = DataUtils.buildRangeFromDataPoint(dp);
        assertEquals(start, range.getMinimum());
        assertEquals(effective, range.getMaximum());
    }

    @Test
    void testBuildRangeFromDataPoint_OnlyEffective() {
        Instant effective = Instant.parse("2023-01-01T11:00:00Z");

        DataPoint dp = new DataPoint("id", "obsId", "obsType", "dataType", Instant.now(), effective, Map.of());

        Range<Instant> range = DataUtils.buildRangeFromDataPoint(dp);
        assertEquals(effective, range.getMinimum());
        assertEquals(effective, range.getMaximum());
    }

    @Test
    void testBuildRangeFromDataPoint_OnlyStartNoEffective() {
        Instant start = Instant.parse("2023-01-01T10:00:00Z");

        DataPoint dp = new DataPoint("id", "obsId", "obsType", "dataType", Instant.now(), null,
                Map.of("startTime", start.toString()));

        Range<Instant> range = DataUtils.buildRangeFromDataPoint(dp);
        assertEquals(start, range.getMinimum());
        assertEquals(start, range.getMaximum());
    }

    @Test
    void testBuildRangeFromDataPoint_NoTimeData() {
        DataPoint dp = new DataPoint("id", "obsId", "obsType", "dataType", Instant.now(), null, Map.of());
        assertThrows(IllegalArgumentException.class, () -> DataUtils.buildRangeFromDataPoint(dp));
    }
}
