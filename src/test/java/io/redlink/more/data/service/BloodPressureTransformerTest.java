package io.redlink.more.data.service;

import io.redlink.more.data.custom.model.GarminDataPoint;
import io.redlink.more.data.model.DataPoint;
import io.redlink.more.data.model.DataType;
import io.redlink.more.data.model.Observation;
import io.redlink.more.data.model.garmin.GarminSummaryType;
import io.redlink.more.data.model.garmin.transformation.GarminBloodPressure;
import io.redlink.more.data.model.garmin.transformation.GarminTimeData;
import io.redlink.more.data.model.scheduler.Event;
import io.redlink.more.data.transformers.garmin.BloodPressureTransformer;
import org.apache.commons.lang3.Range;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.redlink.more.data.util.ElasticUtils.Constants.GARMIN_SUMMARY_ID_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BloodPressureTransformerTest extends AbstractGarminTransformerTestBase<BloodPressureTransformerTest.TestTransformer> {

    @Override
    protected TestTransformer createTransformer() {
        return new TestTransformer();
    }

    @Test
    @DisplayName("getSupportedType: returns BLOODPRESSURES")
    void getSupportedType_returnsBloodPressures() {
        assertThat(transformer.getSupportedType()).isEqualTo(GarminSummaryType.BLOODPRESSURES);
    }

    static class TestTransformer extends BloodPressureTransformer {
        public List<DataPoint> expose(List<Observation> observations, String summaryId, GarminTimeData<?> data) {
            return super.transformGarminTimeDataToDataPoint(observations, summaryId, DataType.BLOOD_PRESSURE, data);
        }

        public List<DataPoint> exposeFilter(List<Range<Instant>> ranges, List<DataPoint> bulk) {
            return super.filterDataPointByTimeRange(ranges, bulk);
        }
    }


    @Test
    @DisplayName("transformGarminTimeDataToDataPoint: builds DataPoint with BP data and summary id")
    void transformGarminTimeDataToDataPoint_buildsDataPoint() {
        // Subclass to expose protected helper


        Observation observation = new Observation(
                1,
                null,
                "Test Obs",
                "garmin-bp",
                null,
                null,
                null,
                null,
                null,
                false,
                false,
                false,
                Set.of()
        );

        GarminBloodPressure bp = new GarminBloodPressure(120, 80, 65, GarminBloodPressure.SourceType.DEVICE);
        Instant ts = Instant.parse("2024-03-01T12:34:56Z");
        GarminTimeData<GarminBloodPressure> timeData = new GarminTimeData<>(ts, bp);

        List<DataPoint> results = this.transformer.expose(List.of(observation), "sum-123", timeData);

        assertThat(results).hasSize(1);
        DataPoint dp = results.get(0);
        assertThat(dp.observationId()).isEqualTo("1");
        assertThat(dp.dataType()).isEqualTo(DataType.BLOOD_PRESSURE.name());
        assertThat(dp.effectiveDateTime()).isEqualTo(ts);
        assertThat(dp.data()).containsEntry("systolic", 120)
                .containsEntry("diastolic", 80)
                .containsEntry("pulse", 65)
                .containsEntry("sourceType", "DEVICE")
                .containsEntry(GARMIN_SUMMARY_ID_KEY, "sum-123");
    }

    @Test
    @DisplayName("transform: returns datapoint using measurement time when observation overlaps Garmin time range")
    void transform_returnsDataPoint_whenObservationOverlaps() {
        GarminDataPoint garmin = mock(GarminDataPoint.class);

        // Garmin time range (for filtering)
        int start = (int) Instant.parse("2024-03-01T12:00:00Z").getEpochSecond();
        int duration = 600; // 10 min
        int offset = 0;

        when(garmin.getStartTimeInSeconds()).thenReturn(start);
        when(garmin.getDurationInSeconds()).thenReturn(duration);
        when(garmin.getStartTimeOffsetInSeconds()).thenReturn(offset);

        // Measurement timestamp used as effective time
        Instant measurement = Instant.parse("2024-03-01T12:05:00Z");
        when(garmin.getStartTimeInSeconds()).thenReturn((int) measurement.getEpochSecond());
        when(garmin.getStartTimeOffsetInSeconds()).thenReturn(0);

        when(garmin.getSummaryId()).thenReturn("sum-abc");

        when(garmin.getSystolic()).thenReturn(118);
        when(garmin.getDiastolic()).thenReturn(79);
        when(garmin.getPulse()).thenReturn(64);
        when(garmin.getSourceType()).thenReturn(GarminDataPoint.SourceTypeEnum.MANUAL);

        // Observation that overlaps Garmin range
        Event event = new Event()
                .setDateStart(Instant.parse("2024-03-01T11:59:00Z"))
                .setDateEnd(Instant.parse("2024-03-01T12:20:00Z"));

        Observation observation = new Observation(
                42,
                null,
                "BP Observation",
                "garmin-bp",
                null,
                null,
                event,
                null,
                null,
                false,
                false,
                false,
                Set.of()
        );

        List<DataPoint> result = transformer.transform(List.of(observation), garmin, Instant.MIN, Instant.MAX);

        assertThat(result).hasSize(1);

        DataPoint dp = result.get(0);
        assertThat(dp.observationId()).isEqualTo("42");
        assertThat(dp.dataType()).isEqualTo(DataType.BLOOD_PRESSURE.name());
        assertThat(dp.effectiveDateTime()).isEqualTo(measurement);

        Map<String, Object> data = dp.data();
        assertThat(data).containsEntry("systolic", 118);
        assertThat(data).containsEntry("diastolic", 79);
        assertThat(data).containsEntry("pulse", 64);
        // Enum serialized as string
        assertThat(data).containsEntry("sourceType", "MANUAL");
        // Summary id included
        assertThat(data).containsEntry(GARMIN_SUMMARY_ID_KEY, "sum-abc");
    }

    @Test
    @DisplayName("filterDataPointByTimeRange: returns data as-is (no filtering)")
    void filterDataPointByTimeRange_returnsInput() throws Exception {
        DataPoint dp1 = new DataPoint("id1", "1", "garmin-bp", DataType.BLOOD_PRESSURE.name(), Instant.now(), Instant.now(), Map.of());
        DataPoint dp2 = new DataPoint("id2", "2", "garmin-bp", DataType.BLOOD_PRESSURE.name(), Instant.now(), Instant.now(), Map.of());

        List<DataPoint> input = List.of(dp1, dp2);
        List<DataPoint> out = transformer.exposeFilter(List.of(Range.of(Instant.EPOCH, Instant.now())), input);

        assertThat(out).containsExactlyElementsOf(input);
    }

    @Test
    @DisplayName("transform: returns empty when no overlapping observation")
    void transform_returnsEmpty_whenNoOverlap() {
        GarminDataPoint garmin = mock(GarminDataPoint.class);

        int start = (int) Instant.parse("2024-03-01T12:00:00Z").getEpochSecond();
        int duration = 300; // 5 min
        int offset = 0;

        when(garmin.getStartTimeInSeconds()).thenReturn(start);
        when(garmin.getDurationInSeconds()).thenReturn(duration);
        when(garmin.getStartTimeOffsetInSeconds()).thenReturn(offset);

        when(garmin.getStartTimeInSeconds()).thenReturn((int) Instant.parse("2024-03-01T12:02:00Z").getEpochSecond());
        when(garmin.getStartTimeOffsetInSeconds()).thenReturn(0);

        // No overlap: observation is before Garmin time range
        Event event = new Event()
                .setDateStart(Instant.parse("2024-03-01T11:00:00Z"))
                .setDateEnd(Instant.parse("2024-03-01T11:10:00Z"));

        Observation observation = new Observation(
                5,
                null,
                "BP Observation",
                "garmin-bp",
                null,
                null,
                event,
                null,
                null,
                false,
                false,
                false,
                Set.of()
        );

        List<DataPoint> result = transformer.transform(List.of(observation), garmin, Instant.MIN, Instant.MAX);
        assertThat(result).isEmpty();
    }
}
