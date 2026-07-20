package io.redlink.more.data.service;

import io.redlink.more.data.model.DataPoint;
import io.redlink.more.data.model.DataType;
import io.redlink.more.data.model.Observation;
import io.redlink.more.data.model.garmin.transformation.GarminTimeData;
import io.redlink.more.data.transformers.garmin.AbstractGarminTransformer;
import io.redlink.more.data.transformers.garmin.HeartRateTransformers;
import org.apache.commons.lang3.Range;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class HeartRateTransformersTest extends AbstractGarminTransformerTestBase<HeartRateTransformersTest.TestHeartRateTransformers> {

    public static class TestHeartRateTransformers extends HeartRateTransformers {

        List<DataPoint> exposeFilterByTimeRange(List<Range<Instant>> validTimeRanges, List<DataPoint> dataBulk) {
            return super.filterDataPointByTimeRange(validTimeRanges, dataBulk);
        }
    }

    @Override
    protected TestHeartRateTransformers createTransformer() {
        return new TestHeartRateTransformers();
    }

    @Test
    @DisplayName("transformGarminTimeDataToDataPoint: correctly transforms GarminTimeData to DataPoint")
    void transformToDataPointGarminTimeDataToDataPoint_correctlyTransformsData() throws Exception {
        Integer observationId = 123;
        String observationType = "garmin-heartrate-type";
        String summaryId = "summary-456";
        DataType dataType = DataType.HEARTRATE;
        Instant timestamp = Instant.parse("2024-01-15T10:30:00Z");
        Integer heartRateValue = 75;

        Observation observation = new Observation(
                observationId,
                null,
                "Epoch Observation",
                observationType,
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
        GarminTimeData<Integer> garminTimeData = new GarminTimeData<>(timestamp, heartRateValue);

        var method = AbstractGarminTransformer.class.getDeclaredMethod(
                "transformGarminTimeDataToDataPoint",
                List.class,
                String.class,
                DataType.class,
                GarminTimeData.class
        );
        method.setAccessible(true);

        List<DataPoint> results = (List<DataPoint>) method.invoke(
                transformer,
                List.of(observation),
                summaryId,
                dataType,
                garminTimeData
        );

        assertThat(results).isNotNull();
        assertThat(results).hasSize(1);

        DataPoint result = results.get(0);
        assertThat(result).isNotNull();
        assertThat(result.observationId()).isEqualTo(String.valueOf(observationId));
        assertThat(result.observationType()).isEqualTo(observationType);
        assertThat(result.dataType()).isEqualTo(dataType.name());
        assertThat(result.effectiveDateTime()).isEqualTo(timestamp);
        assertThat(result.serverTime()).isNotNull();
        assertThat(result.data()).isNotNull();
        assertThat(result.data()).isInstanceOf(Map.class);

        Map<String, Object> dataMap = result.data();
        assertThat(dataMap.get(dataType.dataType)).isEqualTo(heartRateValue);
    }

    @Test
    @DisplayName("filterDataPointByTimeRange: keeps datapoints whose effectiveDateTime lies within any valid range")
    void filterDataPointByTimeRange_keepsDatapointsWithinRange() {
        Instant start = Instant.parse("2024-01-15T10:00:00Z");
        Instant end = Instant.parse("2024-01-15T11:00:00Z");

        Range<Instant> validRange = Range.of(start, end);

        DataPoint inRange = new DataPoint(
                "dp-1",
                "1",
                "garmin-heartrate-type",
                DataType.HEARTRATE.name(),
                Instant.now(),
                Instant.parse("2024-01-15T10:30:00Z"),
                Map.of()
        );

        DataPoint outOfRange = new DataPoint(
                "dp-2",
                "2",
                "garmin-heartrate-type",
                DataType.HEARTRATE.name(),
                Instant.now(),
                Instant.parse("2024-01-15T12:00:00Z"),
                Map.of()
        );

        List<DataPoint> result = transformer.exposeFilterByTimeRange(List.of(validRange), List.of(inRange, outOfRange));

        assertThat(result)
                .hasSize(1)
                .first()
                .isEqualTo(inRange);
    }

    @Test
    @DisplayName("filterDataPointByTimeRange: returns empty list when no datapoint lies within valid ranges")
    void filterDataPointByTimeRange_returnsEmptyWhenNoMatch() {
        Instant start = Instant.parse("2024-01-15T10:00:00Z");
        Instant end = Instant.parse("2024-01-15T11:00:00Z");

        Range<Instant> validRange = Range.of(start, end);

        DataPoint outOfRange1 = new DataPoint(
                "dp-1",
                "1",
                "garmin-heartrate-type",
                DataType.HEARTRATE.name(),
                Instant.now(),
                Instant.parse("2024-01-15T09:00:00Z"),
                Map.of()
        );

        DataPoint outOfRange2 = new DataPoint(
                "dp-2",
                "2",
                "garmin-heartrate-type",
                DataType.HEARTRATE.name(),
                Instant.now(),
                Instant.parse("2024-01-15T12:00:00Z"),
                Map.of()
        );

        List<DataPoint> result = transformer.exposeFilterByTimeRange(List.of(validRange), List.of(outOfRange1, outOfRange2));

        assertThat(result).isEmpty();
    }
}