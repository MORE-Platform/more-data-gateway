package io.redlink.more.data.service;

import io.redlink.more.data.custom.model.GarminDataPoint;
import io.redlink.more.data.model.DataPoint;
import io.redlink.more.data.model.DataType;
import io.redlink.more.data.model.Observation;
import io.redlink.more.data.model.garmin.GarminSummaryType;
import io.redlink.more.data.model.scheduler.Event;
import io.redlink.more.data.transformers.garmin.steps.EpochStepDataTransformer;
import org.apache.commons.lang3.Range;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class EpochStepDataTransformerTest extends AbstractGarminTransformerTestBase<EpochStepDataTransformerTest.TestEpochStepDataTransformer> {

    @Override
    protected TestEpochStepDataTransformer createTransformer() {
        return new TestEpochStepDataTransformer();
    }

    public static class TestEpochStepDataTransformer extends EpochStepDataTransformer {
        List<DataPoint> exposeFilterByTimeRange(List<Range<Instant>> validTimeRanges, List<DataPoint> dataBulk) {
            return super.filterDataPointByTimeRange(validTimeRanges, dataBulk);
        }
    }

    @Test
    @DisplayName("getSupportedType returns EPOCHS")
    void getSupportedType_returnsEpochs() {
        assertThat(transformer.getSupportedType()).isEqualTo(GarminSummaryType.EPOCHS);
    }

    @Test
    @DisplayName("transform: returns one EPOCH_STEPS DataPoint with end timestamp and step fields")
    void transform_returnsEpochStepsDataPoint() {
        GarminDataPoint garminDataPoint = new GarminDataPoint()
                .summaryId("summary-steps-epoch-1")
                .startTimeInSeconds(1_700_100_000)
                .durationInSeconds(300)
                .startTimeOffsetInSeconds(0)
                .steps(120)
                .distanceInMeters(150.5);

        Instant start = Instant.ofEpochSecond(1_700_100_000);
        Instant end = start.plusSeconds(300);

        Event schedule = new Event()
                .setDateStart(start.minusSeconds(60))
                .setDateEnd(end.plusSeconds(60));

        Observation observation = new Observation(
                1,
                null,
                "Epoch Steps Observation",
                DataType.EPOCH_STEPS.dataType,
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

        List<DataPoint> result = transformer.transform(List.of(observation), garminDataPoint, start.minusSeconds(600), end.plusSeconds(600));

        assertThat(result).hasSize(1);
        DataPoint dp = result.get(0);
        assertThat(dp.dataType()).isEqualTo(DataType.EPOCH_STEPS.name());
        assertThat(dp.observationId()).isEqualTo("1");
        assertThat(dp.effectiveDateTime()).isEqualTo(start);

        Map<String, Object> data = dp.data();
        assertThat(data).isNotNull();
        assertThat(data.get("steps")).isEqualTo(120);
        assertThat(data.get("distanceInMeters")).isEqualTo(150.5);
    }

    @Test
    @DisplayName("filterDataPointByTimeRange: returns input unchanged (no filtering)")
    void filterDataPointByTimeRange_noFiltering() {
        DataPoint a = new DataPoint("a", "1", "type", DataType.EPOCH_STEPS.name(), Instant.now(), Instant.now(), Map.of());
        DataPoint b = new DataPoint("b", "1", "type", DataType.EPOCH_STEPS.name(), Instant.now(), Instant.now(), Map.of());
        List<DataPoint> input = List.of(a, b);

        List<DataPoint> out = transformer.exposeFilterByTimeRange(List.of(Range.of(Instant.EPOCH, Instant.now())), input);
        assertThat(out).isEqualTo(input);
    }
}
