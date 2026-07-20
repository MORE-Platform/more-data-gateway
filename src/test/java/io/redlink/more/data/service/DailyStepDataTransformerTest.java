package io.redlink.more.data.service;

import io.redlink.more.data.custom.model.GarminDataPoint;
import io.redlink.more.data.model.DataPoint;
import io.redlink.more.data.model.DataType;
import io.redlink.more.data.model.Observation;
import io.redlink.more.data.model.garmin.GarminSummaryType;
import io.redlink.more.data.model.scheduler.Event;
import io.redlink.more.data.transformers.garmin.steps.DailyStepDataTransformer;
import org.apache.commons.lang3.Range;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DailyStepDataTransformerTest extends AbstractGarminTransformerTestBase<DailyStepDataTransformerTest.TestDailyStepDataTransformer> {

    @Override
    protected TestDailyStepDataTransformer createTransformer() {
        return new TestDailyStepDataTransformer();
    }

    public static class TestDailyStepDataTransformer extends DailyStepDataTransformer {
        List<DataPoint> exposeFilterByTimeRange(List<Range<Instant>> validTimeRanges, List<DataPoint> dataBulk) {
            return super.filterDataPointByTimeRange(validTimeRanges, dataBulk);
        }
    }

    @Test
    @DisplayName("getSupportedType returns DAILIES")
    void getSupportedType_returnsDailies() {
        assertThat(transformer.getSupportedType()).isEqualTo(GarminSummaryType.DAILIES);
    }

    @Test
    @DisplayName("transform: returns one DAILY_STEPS DataPoint with end timestamp and step fields")
    void transform_returnsDailyStepsDataPoint() {
        // Given a Garmin datapoint with steps and a time window
        GarminDataPoint garminDataPoint = new GarminDataPoint()
                .summaryId("summary-steps-1")
                .startTimeInSeconds(1_700_000_000)
                .durationInSeconds(3_600)
                .startTimeOffsetInSeconds(0)
                .steps(12345)
                .stepsGoal(10000)
                .distanceInMeters(7654.32);

        Instant start = Instant.ofEpochSecond(1_700_000_000);
        Instant end = start.plusSeconds(3_600);

        Event schedule = new Event()
                .setDateStart(start.minusSeconds(600))
                .setDateEnd(end.plusSeconds(600));

        Observation observation = new Observation(
                1,
                null,
                "Daily Steps Observation",
                DataType.DAILY_STEPS.dataType,
                null,
                null,
                schedule,
                start,
                start,
                false,
                false,
                false,
                Set.of()
        );

        // When
        List<DataPoint> result = transformer.transform(List.of(observation), garminDataPoint, start.minusSeconds(3600), end.plusSeconds(3600));

        // Then
        assertThat(result).hasSize(1);
        DataPoint dp = result.get(0);
        assertThat(dp.dataType()).isEqualTo(DataType.DAILY_STEPS.name());
        assertThat(dp.observationId()).isEqualTo("1");
        // Effective time is the start of the window
        assertThat(dp.effectiveDateTime()).isEqualTo(start);

        Map<String, Object> data = dp.data();
        assertThat(data).isNotNull();
        assertThat(data.get("steps")).isEqualTo(12345);
        // verify renamed key is present and old key is absent
        assertThat(data.get("stepsGoal")).isEqualTo(10000);
        assertThat(data).doesNotContainKey("stepGoal");
        assertThat(data.get("distanceInMeters")).isEqualTo(7654.32);
    }

    @Test
    @DisplayName("filterDataPointByTimeRange: returns input unchanged (no filtering)")
    void filterDataPointByTimeRange_noFiltering() {
        DataPoint a = new DataPoint("a", "1", "type", DataType.DAILY_STEPS.name(), Instant.now(), Instant.now(), Map.of());
        DataPoint b = new DataPoint("b", "1", "type", DataType.DAILY_STEPS.name(), Instant.now(), Instant.now(), Map.of());
        List<DataPoint> input = List.of(a, b);

        List<DataPoint> out = this.transformer.exposeFilterByTimeRange(List.of(Range.of(Instant.EPOCH, Instant.now())), input);
        assertThat(out).isEqualTo(input);
    }
}
