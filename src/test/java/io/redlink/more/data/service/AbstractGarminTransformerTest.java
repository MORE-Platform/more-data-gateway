package io.redlink.more.data.service;

import io.redlink.more.data.custom.model.GarminDataPoint;
import io.redlink.more.data.model.DataPoint;
import io.redlink.more.data.model.DataType;
import io.redlink.more.data.model.Observation;
import io.redlink.more.data.model.garmin.GarminSummaryType;
import io.redlink.more.data.model.garmin.transformation.GarminTimeData;
import io.redlink.more.data.model.scheduler.Event;
import io.redlink.more.data.transformers.garmin.AbstractGarminTransformer;
import org.apache.commons.lang3.Range;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AbstractGarminTransformerTest extends AbstractGarminTransformerTestBase<AbstractGarminTransformerTest.TestGarminTransformer> {

    @Override
    protected TestGarminTransformer createTransformer() {
        return new TestGarminTransformer();
    }

    /**
     * Simple concrete subclass to expose the protected / private behaviour for testing.
     */
    public static class TestGarminTransformer extends AbstractGarminTransformer {

        private TestGarminTransformer() {
            super();
        }

        @Override
        public GarminSummaryType getSupportedType() {
            return null;
        }

        @Override
        public String getObservationType() {
            return "garmin-observation";
        }

        @Override
        protected List<DataPoint> transformToDataPoint(List<Observation> observations, GarminDataPoint garminDataPoint) {
            // Create a simple DataPoint for each observation so that we can
            // test the behaviour of the AbstractGarminTransformer#transform
            // method without depending on concrete subclass logic.
            return observations.stream()
                    .map(obs -> new DataPoint(
                            "dp-" + obs.observationId(),
                            String.valueOf(obs.observationId()),
                            obs.type(),
                            DataType.HEARTRATE.name(),
                            Instant.now(),
                            // For testing we simply align the datapoint timestamp with the
                            // Garmin datapoint start time so that it lies inside the
                            // generated time ranges when needed.
                            Instant.ofEpochSecond(garminDataPoint.getStartTimeInSeconds()),
                            Map.of()
                    ))
                    .toList();
        }

        @Override
        protected List<DataPoint> filterDataPointByTimeRange(List<Range<Instant>> validTimeRanges, List<DataPoint> dataBulk) {
            // Only keep datapoints whose effectiveDateTime falls into at least
            // one of the valid ranges. This mirrors the behaviour of
            // HeartRateTransformers and allows us to verify that
            // AbstractGarminTransformer#transform applies time range
            // filtering.
            return dataBulk.stream()
                    .filter(dp -> validTimeRanges.stream().anyMatch(r -> r.contains(dp.effectiveDateTime())))
                    .toList();
        }

        List<DataPoint> exposeTransformTimeData(List<Observation> observations,
                                                String summaryId,
                                                DataType dataType,
                                                GarminTimeData<?> timeData) {
            return transformGarminTimeDataToDataPoint(observations, summaryId, dataType, timeData);
        }

        OffsetDateTime exposeRecordingTimestamp(GarminDataPoint garminDataPoint) {
            return recordingTimestamp(garminDataPoint);
        }

        Instant exposeCalculateEndInstant(GarminDataPoint garminDataPoint) {
            return calculateEndInstant(garminDataPoint);
        }
    }

    @Test
    @DisplayName("recordingTimestamp: builds OffsetDateTime from startTimeInSeconds and offset")
    void recordingTimestamp_buildsCorrectOffsetDateTime() {
        GarminDataPoint garminDataPoint = mock(GarminDataPoint.class);

        int startTimeInSeconds = 1_600_000_000;
        int offsetSeconds = 3600; // +01:00

        when(garminDataPoint.getStartTimeInSeconds()).thenReturn(startTimeInSeconds);
        when(garminDataPoint.getStartTimeOffsetInSeconds()).thenReturn(offsetSeconds);

        OffsetDateTime result = transformer.exposeRecordingTimestamp(garminDataPoint);

        Instant expectedInstant = Instant.ofEpochSecond(startTimeInSeconds);
        ZoneOffset expectedOffset = ZoneOffset.ofTotalSeconds(offsetSeconds);

        assertThat(result.toInstant()).isEqualTo(expectedInstant);
        assertThat(result.getOffset()).isEqualTo(expectedOffset);
    }

    @Test
    @DisplayName("endDateTime: adds durationInSeconds to startTimeInSeconds and uses same offset")
    void endDateTime_buildsCorrectOffsetDateTime() {
        GarminDataPoint garminDataPoint = mock(GarminDataPoint.class);

        int startTimeInSeconds = 1_600_000_000;
        int durationInSeconds = 600;
        int offsetSeconds = 0; // UTC

        when(garminDataPoint.getStartTimeInSeconds()).thenReturn(startTimeInSeconds);
        when(garminDataPoint.getDurationInSeconds()).thenReturn(durationInSeconds);
        when(garminDataPoint.getStartTimeOffsetInSeconds()).thenReturn(offsetSeconds);

        Instant result = transformer.exposeCalculateEndInstant(garminDataPoint);

        Instant expectedInstant = Instant.ofEpochSecond(startTimeInSeconds + durationInSeconds);

        assertThat(result).isEqualTo(expectedInstant);
    }

    @Test
    @DisplayName("transformGarminTimeDataToDataPoint: builds DataPoint with correct fields and unique id")
    void transformToDataPointGarminTimeDataToDataPoint_buildsCorrectDataPoint() throws Exception {
        String observationId = "123";
        String observationType = "garmin_test";
        String summaryId = "summary-xyz";

        DataType dataType = DataType.HEARTRATE;

        Instant timestamp = Instant.parse("2023-01-01T12:00:00Z");

        @SuppressWarnings("unchecked")
        GarminTimeData<Object> timeData = (GarminTimeData<Object>) mock(GarminTimeData.class);
        when(timeData.timestamp()).thenReturn(timestamp);
        when(timeData.dataToMap(any(), any())).thenReturn(Map.of("someKey", "someValue"));

        Instant before = Instant.now();

        Event scheduleEvent = new Event()
                .setDateStart(timestamp.minus(1, ChronoUnit.HOURS))
                .setDateEnd(timestamp.plus(1, ChronoUnit.HOURS));

        Observation observation = new Observation(
                Integer.parseInt(observationId),
                null,
                "Test Observation",
                observationType,
                null,
                null,
                scheduleEvent,
                timestamp.minus(1, ChronoUnit.DAYS),
                timestamp.minus(1, ChronoUnit.DAYS),
                false,
                false,
                false,
                Set.of()
        );

        List<DataPoint> dataPoints1 = transformer.exposeTransformTimeData(
                List.of(observation), summaryId, dataType, timeData
        );

        assertThat(dataPoints1).hasSize(1);

        DataPoint dataPoint1 = dataPoints1.get(0);

        Instant after = Instant.now();

        Field idField = DataPoint.class.getDeclaredField("datapointId");
        Field obsIdField = DataPoint.class.getDeclaredField("observationId");
        Field obsTypeField = DataPoint.class.getDeclaredField("observationType");
        Field dataTypeField = DataPoint.class.getDeclaredField("dataType");
        Field createdAtField = DataPoint.class.getDeclaredField("serverTime");
        Field timestampField = DataPoint.class.getDeclaredField("effectiveDateTime");
        Field dataField = DataPoint.class.getDeclaredField("data");

        idField.setAccessible(true);
        obsIdField.setAccessible(true);
        obsTypeField.setAccessible(true);
        dataTypeField.setAccessible(true);
        createdAtField.setAccessible(true);
        timestampField.setAccessible(true);
        dataField.setAccessible(true);

        String id1 = (String) idField.get(dataPoint1);
        String obsId = (String) obsIdField.get(dataPoint1);
        String obsType = (String) obsTypeField.get(dataPoint1);
        String storedDataType = (String) dataTypeField.get(dataPoint1);
        Instant createdAt = (Instant) createdAtField.get(dataPoint1);
        Instant storedTimestamp = (Instant) timestampField.get(dataPoint1);
        @SuppressWarnings("unchecked")
        Map<String, Object> storedData = (Map<String, Object>) dataField.get(dataPoint1);

        assertThat(obsId).isEqualTo(observationId);
        assertThat(obsType).isEqualTo(observationType);
        assertThat(storedDataType).isEqualTo(dataType.name());
        assertThat(storedTimestamp).isEqualTo(timestamp);
        assertThat(storedData).containsEntry("someKey", "someValue");

        assertThat(createdAt).isBetween(before.minusSeconds(5), after.plusSeconds(5));

        List<DataPoint> dataPoints2 = transformer.exposeTransformTimeData(
                List.of(observation), summaryId, dataType, timeData
        );

        assertThat(dataPoints2).hasSize(1);

        String id2 = (String) idField.get(dataPoints2.get(0));

        assertThat(id2).isNotEqualTo(id1);
    }

    @Test
    @DisplayName("transform: returns datapoints when observation schedule overlaps Garmin datapoint range")
    void transform_returnsDataPoints_whenObservationOverlaps() {
        GarminDataPoint garminDataPoint = mock(GarminDataPoint.class);

        int startTimeInSeconds = 1_700_100_000;
        int durationInSeconds = 1_200; // 20 minutes
        int offsetSeconds = 0;

        when(garminDataPoint.getStartTimeInSeconds()).thenReturn(startTimeInSeconds);
        when(garminDataPoint.getDurationInSeconds()).thenReturn(durationInSeconds);
        when(garminDataPoint.getStartTimeOffsetInSeconds()).thenReturn(offsetSeconds);

        Instant garminStart = Instant.ofEpochSecond(startTimeInSeconds);
        Instant garminEnd = garminStart.plusSeconds(durationInSeconds);

        // Observation window overlaps the Garmin datapoint range
        Event event = new Event()
                .setDateStart(garminStart.minusSeconds(300))
                .setDateEnd(garminEnd.plusSeconds(300));

        Observation observation = new Observation(
                1,
                null,
                "Overlapping Observation",
                "garmin_test",
                null,
                null,
                event,
                garminStart.minusSeconds(300),
                garminStart.minusSeconds(300),
                false,
                false,
                false,
                Set.of()
        );

        List<DataPoint> result = transformer.transform(
                List.of(observation),
                garminDataPoint,
                garminStart.minusSeconds(10_000),
                garminEnd.plusSeconds(10_000)
        );

        assertThat(result).hasSize(1);
        DataPoint dp = result.get(0);
        assertThat(dp.observationId()).isEqualTo("1");
        assertThat(dp.effectiveDateTime()).isBetween(garminStart.minusSeconds(1), garminEnd.plusSeconds(1));
    }

    @Test
    @DisplayName("transform: returns empty list when no observation schedule overlaps the Garmin datapoint range")
    void transform_returnsEmpty_whenNoOverlappingObservation() {
        GarminDataPoint garminDataPoint = mock(GarminDataPoint.class);

        int startTimeInSeconds = 1_700_000_000;
        int durationInSeconds = 600;
        int offsetSeconds = 0;

        when(garminDataPoint.getStartTimeInSeconds()).thenReturn(startTimeInSeconds);
        when(garminDataPoint.getDurationInSeconds()).thenReturn(durationInSeconds);
        when(garminDataPoint.getStartTimeOffsetInSeconds()).thenReturn(offsetSeconds);

        Instant garminStart = Instant.ofEpochSecond(startTimeInSeconds);
        Instant garminEnd = garminStart.plusSeconds(durationInSeconds);

        // Observation happens completely before the Garmin range -> no overlap
        Event event = new Event()
                .setDateStart(garminStart.minusSeconds(3_600))
                .setDateEnd(garminStart.minusSeconds(1_800));

        Observation observation = new Observation(
                1,
                null,
                "Test Observation",
                "garmin_test",
                null,
                null,
                event,
                garminStart.minusSeconds(3_600),
                garminStart.minusSeconds(3_600),
                false,
                false,
                false,
                Set.of()
        );

        List<DataPoint> result = transformer.transform(
                List.of(observation),
                garminDataPoint,
                garminStart.minusSeconds(10_000),
                garminEnd.plusSeconds(10_000)
        );

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getGarminDataPointTimeRange: returns range from recordingTimestamp to endDateTime")
    void getGarminDataPointTimeRange_returnsCorrectRange() throws Exception {
        GarminDataPoint garminDataPoint = mock(GarminDataPoint.class);

        int startTimeInSeconds = 1_700_000_000;
        int durationInSeconds = 900;
        int offsetSeconds = 3600;

        when(garminDataPoint.getStartTimeInSeconds()).thenReturn(startTimeInSeconds);
        when(garminDataPoint.getDurationInSeconds()).thenReturn(durationInSeconds);
        when(garminDataPoint.getStartTimeOffsetInSeconds()).thenReturn(offsetSeconds);

        Instant expectedStart = Instant.ofEpochSecond(startTimeInSeconds).atOffset(ZoneOffset.ofTotalSeconds(offsetSeconds)).toInstant();
        Instant expectedEnd = Instant.ofEpochSecond(startTimeInSeconds + durationInSeconds).atOffset(ZoneOffset.ofTotalSeconds(offsetSeconds)).toInstant();

        var method = AbstractGarminTransformer.class.getDeclaredMethod("getGarminDataPointTimeRange", GarminDataPoint.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        Range<Instant> range = (Range<Instant>) method.invoke(transformer, garminDataPoint);

        assertThat(range.getMinimum()).isEqualTo(expectedStart);
        assertThat(range.getMaximum()).isEqualTo(expectedEnd);
    }

    @Test
    @DisplayName("calculateEndInstant: sums all durationInSeconds properties")
    void calculateEndInstant_sumsDurations() {
        GarminDataPoint garminDataPoint = new GarminDataPoint()
                .startTimeInSeconds(1_700_000_000)
                .startTimeOffsetInSeconds(0)
                .durationInSeconds(10)
                .activeTimeInSeconds(20L)
                .totalNapDurationInSeconds(30L)
                .unmeasurableSleepInSeconds(40L)
                .deepSleepDurationInSeconds(50L)
                .lightSleepDurationInSeconds(60L)
                .remSleepInSeconds(70L)
                .awakeDurationInSeconds(80L);

        Instant result = transformer.exposeCalculateEndInstant(garminDataPoint);

        assertThat(result).isEqualTo(Instant.ofEpochSecond(1_700_000_000 + 10));
    }

    @Test
    @DisplayName("calculateEndInstant: returns startTime if no durations found")
    void calculateEndInstant_returnsStartTime_ifNoDurations() {
        GarminDataPoint garminDataPoint = new GarminDataPoint()
                .startTimeInSeconds(1_700_000_000)
                .startTimeOffsetInSeconds(0);

        Instant result = transformer.exposeCalculateEndInstant(garminDataPoint);

        assertThat(result).isEqualTo(Instant.ofEpochSecond(1_700_000_000));
    }
}