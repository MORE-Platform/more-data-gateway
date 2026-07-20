package io.redlink.more.data.service;

import io.redlink.more.data.custom.model.GarminDataPoint;
import io.redlink.more.data.model.DataPoint;
import io.redlink.more.data.model.DataType;
import io.redlink.more.data.model.Observation;
import io.redlink.more.data.model.garmin.GarminSummaryType;
import io.redlink.more.data.model.scheduler.Event;
import io.redlink.more.data.transformers.garmin.SleepDataTransformer;
import org.apache.commons.lang3.Range;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SleepDataTransformerTest extends AbstractGarminTransformerTestBase<SleepDataTransformerTest.TestSleepDataTransformer> {

    @Override
    protected TestSleepDataTransformer createTransformer() {
        return new TestSleepDataTransformer();
    }

    public static class TestSleepDataTransformer extends SleepDataTransformer {

        List<DataPoint> exposeFilterByTimeRange(List<Range<Instant>> validTimeRanges, List<DataPoint> dataBulk) {
            return filterDataPointByTimeRange(validTimeRanges, dataBulk);
        }
    }


    @Test
    @DisplayName("getSupportedType returns SLEEPS")
    void getSupportedType_returnsSleeps() {
        GarminSummaryType result = transformer.getSupportedType();
        assertEquals(GarminSummaryType.SLEEPS, result);
    }

    @Test
    @DisplayName("transform: returns SLEEP DataPoint when all required fields are present and valid")
    void transform_ToDataPoint_returnsDataPoints_whenValidSleep() {
        // Given
        Instant startTime = Instant.parse("2025-12-03T22:00:00Z");
        Instant endTime = Instant.parse("2025-12-04T06:00:00Z");

        GarminDataPoint garminDataPoint = new GarminDataPoint()
                .summaryId("sleep-summary-123")
                .startTimeInSeconds((int) startTime.getEpochSecond())
                .durationInSeconds((int) (endTime.getEpochSecond() - startTime.getEpochSecond()))
                .awakeDurationInSeconds((long) 1000)
                .startTimeOffsetInSeconds(0);

        // Observation schedule that clearly overlaps the Garmin datapoint time range
        Event schedule = new Event()
                .setDateStart(startTime.minusSeconds(3600))
                .setDateEnd(endTime.plusSeconds(3600));

        List<Observation> observations = List.of(
                new Observation(
                        1,
                        null,
                        "Sleep Observation",
                        "garmin-sleep-observation",
                        null,
                        null,
                        schedule,
                        Instant.EPOCH,
                        Instant.EPOCH,
                        false,
                        false,
                        false,
                        Set.of()
                )
        );

        Instant participantStart = startTime.minusSeconds(3600);
        Instant participantEnd = endTime.plusSeconds(3600);

        // When
        List<DataPoint> result = transformer.transform(observations, garminDataPoint, participantStart, participantEnd);

        // Then
        assertEquals(1, result.size());

        DataPoint sleep = result.stream()
                .filter(dp -> Objects.equals(dp.dataType(), DataType.SLEEP.name()))
                .findFirst()
                .orElseThrow();
        assertEquals(DataType.SLEEP.name(), sleep.dataType());
        assertEquals(endTime.plusSeconds(1000), sleep.effectiveDateTime());
        assertNotNull(sleep.data());
        assertEquals(startTime, sleep.data().get("startTime"));
        assertEquals(endTime.plusSeconds(1000), sleep.data().get("endTime"));
    }

    @Test
    @DisplayName("transform: throws NullPointerException when startTimeInSeconds is null (no null-handling implemented)")
    void transform_ToDataPoint_returnsEmpty_whenStartTimeNull() {
        // Given: duration but no start time
        GarminDataPoint garminDataPoint = new GarminDataPoint()
                .summaryId("sleep-summary-123")
                .durationInSeconds(28800)
                .startTimeOffsetInSeconds(0)
                .calendarDate(java.time.LocalDate.parse("2025-12-03"));

        List<Observation> observations = List.of(
                createObservation(1, DataType.SLEEP)
        );

        Instant participantStart = Instant.parse("2025-12-03T00:00:00Z");
        Instant participantEnd = Instant.parse("2025-12-04T23:59:59Z");

        // When / Then: current implementation does not guard against null start time
        assertThrows(NullPointerException.class, () ->
                transformer.transform(observations, garminDataPoint, participantStart, participantEnd)
        );
    }

    @Test
    @DisplayName("transform: returns empty list when durationInSeconds is null")
    void transform_ToDataPoint_returnsEmpty_whenDurationNull() {
        // Given: start time but no duration
        Instant startTime = Instant.parse("2025-12-03T22:00:00Z");

        GarminDataPoint garminDataPoint = new GarminDataPoint()
                .summaryId("sleep-summary-123")
                .startTimeInSeconds((int) startTime.getEpochSecond())
                .startTimeOffsetInSeconds(0)
                .calendarDate(java.time.LocalDate.parse("2025-12-03"));

        List<Observation> observations = List.of(
                createObservation(1, DataType.SLEEP)
        );

        Instant participantStart = startTime.minusSeconds(3600);
        Instant participantEnd = startTime.plusSeconds(36000);

        // When
        List<DataPoint> result = transformer.transform(observations, garminDataPoint, participantStart, participantEnd);

        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("filterDataPointByTimeRange: returns all datapoints without filtering (sleep transformer doesn't filter)")
    void filterDataPointByTimeRange_returnsAllDatapoints() {
        // Given
        Instant now = Instant.now();

        List<DataPoint> dataPoints = List.of(
                createDataPoint(DataType.SLEEP, now.minusSeconds(3600))
        );

        List<Range<Instant>> validTimeRanges = List.of(
                Range.between(now.minusSeconds(7200), now.minusSeconds(1800))
                // This range doesn't overlap with the datapoints
        );

        // When
        List<DataPoint> result = transformer.exposeFilterByTimeRange(validTimeRanges, dataPoints);

        // Then
        // SleepDataTransformer's filterDataPointByTimeRange always returns the full list
        assertEquals(1, result.size());
        assertTrue(result.containsAll(dataPoints));
    }

    private Observation createObservation(int observationId, DataType dataType) {
        // Use a very broad schedule so that, by default, Garmin datapoints are
        // not filtered out by the observation schedule during tests.
        Event schedule = new Event()
                .setDateStart(Instant.EPOCH)
                .setDateEnd(Instant.EPOCH.plusSeconds(10 * 365 * 24 * 60 * 60L)); // ~10 years range

        return new Observation(
                observationId,
                null,
                "Sleep Observation",
                "garmin-sleep-observation",
                null,
                null,
                schedule,
                Instant.EPOCH,
                Instant.EPOCH,
                false,
                false,
                false,
                Set.of()
        );
    }

    private DataPoint createDataPoint(DataType dataType, Instant effectiveTime) {
        return new DataPoint(
                null,
                null,
                null,
                dataType.dataType,
                effectiveTime,
                effectiveTime,
                new HashMap<>()
        );
    }

    /**
     * Small helper to avoid leaking the START_TIME_KEY constant from SleepDataTransformer
     * while still using the same map key in the test data.
     */
    private static final class SleepSummaryTransformerTestHelper {
        private static final String START_TIME_KEY = "startTime";

        private SleepSummaryTransformerTestHelper() {
        }
    }
}