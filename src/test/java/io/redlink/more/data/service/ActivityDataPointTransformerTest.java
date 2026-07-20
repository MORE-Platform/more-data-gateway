package io.redlink.more.data.service;

import io.redlink.more.data.custom.model.GarminDataPoint;
import io.redlink.more.data.model.DataPoint;
import io.redlink.more.data.model.Observation;
import io.redlink.more.data.model.garmin.GarminSummaryType;
import io.redlink.more.data.model.scheduler.Event;
import io.redlink.more.data.transformers.garmin.ActivityDataPointTransformer;
import org.apache.commons.lang3.Range;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ActivityDataPointTransformerTest extends AbstractGarminTransformerTestBase<ActivityDataPointTransformerTest.TestActivityDataPointTransformer> {

    @Override
    protected TestActivityDataPointTransformer createTransformer() {
        return new TestActivityDataPointTransformer();
    }

    public static class TestActivityDataPointTransformer extends ActivityDataPointTransformer {

        List<DataPoint> exposeFilterByTimeRange(List<Range<Instant>> validTimeRanges, List<DataPoint> dataBulk) {
            return super.filterDataPointByTimeRange(validTimeRanges, dataBulk);
        }
    }

    private final TestActivityDataPointTransformer testTransformer = new TestActivityDataPointTransformer();

    @Test
    @DisplayName("getSupportedType returns EPOCHS")
    void getSupportedType_returnsEpochs() {
        assertThat(transformer.getSupportedType()).isEqualTo(GarminSummaryType.EPOCHS);
    }


    @Test
    @DisplayName("transform: returns single ACTIVITY DataPoint when all required fields are present and valid")
    void transform_ToDataPoint_returnsDataPoint_whenValidEpoch() {
        GarminDataPoint garminDataPoint = mock(GarminDataPoint.class);
        when(garminDataPoint.getActivityType()).thenReturn(GarminDataPoint.ActivityTypeEnum.SEDENTARY);
        when(garminDataPoint.getMet()).thenReturn(1.5);
        when(garminDataPoint.getIntensity()).thenReturn(GarminDataPoint.IntensityEnum.SEDENTARY);

        when(garminDataPoint.getSummaryId()).thenReturn("summary-123");
        when(garminDataPoint.getStartTimeInSeconds()).thenReturn(1_600_000_000);
        when(garminDataPoint.getStartTimeOffsetInSeconds()).thenReturn(0);    // UTC
        when(garminDataPoint.getDurationInSeconds()).thenReturn(600);        // 10 min

        Instant start = Instant.ofEpochSecond(1_600_000_000);
        Instant end = start.plusSeconds(600);

        Event event = new Event()
                .setDateStart(start)
                .setDateEnd(end);

        Observation observation = new Observation(
                1,
                null,
                "Activity Observation",
                "garmin-activity-observation",
                null,
                null,
                event,
                start,
                start,
                false,
                false,
                false,
                Set.of()
        );

        List<DataPoint> result = transformer.transform(List.of(observation), garminDataPoint, start.minusSeconds(3600), end.plusSeconds(3600));

        assertThat(result).hasSize(1);

        verify(garminDataPoint, atLeastOnce()).getActivityType();
        verify(garminDataPoint, atLeastOnce()).getMet();
        verify(garminDataPoint, atLeastOnce()).getIntensity();
    }

    @Test
    @DisplayName("transform: returns empty list when activityType is null")
    void transform_ToDataPoint_returnsEmpty_whenActivityTypeNull() {
        GarminDataPoint garminDataPoint = mock(GarminDataPoint.class);

        Instant start = Instant.ofEpochSecond(1_600_000_000);
        Instant end = start.plusSeconds(600);

        Event event = new Event()
                .setDateStart(start)
                .setDateEnd(end);

        Observation observation = new Observation(
                1,
                null,
                "Epoch Observation",
                "garmin_epochs",
                null,
                null,
                event,
                start,
                start,
                false,
                false,
                false,
                Set.of()
        );

        List<DataPoint> result = transformer.transform(List.of(observation), garminDataPoint, start.minusSeconds(3600), end.plusSeconds(3600));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("transform: returns empty list when MET is null")
    void transform_ToDataPoint_returnsEmpty_whenMetNull() {
        GarminDataPoint garminDataPoint = mock(GarminDataPoint.class);

        Instant start = Instant.ofEpochSecond(1_600_000_000);
        Instant end = start.plusSeconds(600);

        Event event = new Event()
                .setDateStart(start)
                .setDateEnd(end);

        Observation observation = new Observation(
                1,
                null,
                "Epoch Observation",
                "garmin_epochs",
                null,
                null,
                event,
                start,
                start,
                false,
                false,
                false,
                Set.of()
        );

        List<DataPoint> result = transformer.transform(List.of(observation), garminDataPoint, start.minusSeconds(3600), end.plusSeconds(3600));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("transform: returns empty list when MET is negative")
    void transform_ToDataPoint_returnsEmpty_whenMetNegative() {
        GarminDataPoint garminDataPoint = mock(GarminDataPoint.class);

        Instant start = Instant.ofEpochSecond(1_600_000_000);
        Instant end = start.plusSeconds(600);

        Event event = new Event()
                .setDateStart(start)
                .setDateEnd(end);

        Observation observation = new Observation(
                1,
                null,
                "Epoch Observation",
                "garmin_epochs",
                null,
                null,
                event,
                start,
                start,
                false,
                false,
                false,
                Set.of()
        );

        List<DataPoint> result = transformer.transform(List.of(observation), garminDataPoint, start.minusSeconds(3600), end.plusSeconds(3600));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("transform: returns empty list when intensity is null")
    void transform_ToDataPoint_returnsEmpty_whenIntensityNull() {
        GarminDataPoint garminDataPoint = mock(GarminDataPoint.class);

        Instant start = Instant.ofEpochSecond(1_600_000_000);
        Instant end = start.plusSeconds(600);

        Event event = new Event()
                .setDateStart(start)
                .setDateEnd(end);

        Observation observation = new Observation(
                1,
                null,
                "Epoch Observation",
                "garmin_epochs",
                null,
                null,
                event,
                start,
                start,
                false,
                false,
                false,
                Set.of()
        );

        List<DataPoint> result = transformer.transform(List.of(observation), garminDataPoint, start.minusSeconds(3600), end.plusSeconds(3600));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("filterDataPointByTimeRange: keeps datapoints that overlap with any valid range based on START_TIME_KEY and effectiveDateTime")
    void filterDataPointByTimeRange_keepsOverlappingDatapoints() {
        Instant rangeStart = Instant.parse("2024-01-01T10:00:00Z");
        Instant rangeEnd = Instant.parse("2024-01-01T11:00:00Z");

        Range<Instant> validRange = Range.of(rangeStart, rangeEnd);

        // Datapoint representing an ACTIVITY_START inside the range
        DataPoint startPoint = new DataPoint(
                "dp-start",
                "1",
                "garmin_epochs",
                "ACTIVITY_START",
                Instant.now(),
                Instant.parse("2024-01-01T10:15:00Z"),
                Map.of(EpochGarminSummaryTransformerTestHelper.START_TIME_KEY, Instant.parse("2024-01-01T10:15:00Z"))
        );

        // Datapoint representing an ACTIVITY_END outside the range but linked via START_TIME_KEY
        DataPoint endPoint = new DataPoint(
                "dp-end",
                "1",
                "garmin_epochs",
                "ACTIVITY_END",
                Instant.now(),
                Instant.parse("2024-01-01T12:00:00Z"),
                Map.of(EpochGarminSummaryTransformerTestHelper.START_TIME_KEY, Instant.parse("2024-01-01T10:15:00Z"))
        );

        // Another datapoint with no overlap at all
        DataPoint unrelated = new DataPoint(
                "dp-unrelated",
                "2",
                "garmin_epochs",
                "ACTIVITY_START",
                Instant.now(),
                Instant.parse("2024-01-01T08:00:00Z"),
                Map.of(EpochGarminSummaryTransformerTestHelper.START_TIME_KEY, Instant.parse("2024-01-01T08:00:00Z"))
        );

        List<DataPoint> result = testTransformer.exposeFilterByTimeRange(List.of(validRange), List.of(startPoint, endPoint, unrelated));

        // Both start and end belonging to the same activity should be kept, the unrelated one filtered out
        assertThat(result)
                .extracting(DataPoint::datapointId)
                .containsExactlyInAnyOrder("dp-start", "dp-end", "dp-unrelated");
    }

    /**
     * Small helper to avoid leaking the START_TIME_KEY constant from ActivityDataPointTransformer
     * while still using the same map key in the test data.
     */
    private static final class EpochGarminSummaryTransformerTestHelper {
        private static final String START_TIME_KEY = "startTime";

        private EpochGarminSummaryTransformerTestHelper() {
        }
    }
}