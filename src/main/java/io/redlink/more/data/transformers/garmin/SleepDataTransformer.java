package io.redlink.more.data.transformers.garmin;

import io.redlink.more.data.custom.model.GarminDataPoint;
import io.redlink.more.data.model.DataPoint;
import io.redlink.more.data.model.DataType;
import io.redlink.more.data.model.Observation;
import io.redlink.more.data.model.garmin.GarminSummaryType;
import io.redlink.more.data.model.garmin.transformation.GarminSleepData;
import io.redlink.more.data.model.garmin.transformation.GarminTimeData;
import io.redlink.more.data.util.MapperUtils;
import org.apache.commons.lang3.Range;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class SleepDataTransformer extends AbstractGarminTransformer {
    public SleepDataTransformer() {
        super();
    }

    @Override
    public GarminSummaryType getSupportedType() {
        return GarminSummaryType.SLEEPS;
    }

    @Override
    public String getObservationType() {
        return "garmin-sleep-observation";
    }

    @Override
    protected List<DataPoint> transformToDataPoint(List<Observation> observations, GarminDataPoint garminDataPoint) {
        var sleepData = dataToSleep(garminDataPoint);
        return transformGarminTimeDataToDataPoint(
                observations,
                garminDataPoint.getSummaryId(),
                DataType.SLEEP,
                sleepData
        );
    }

    @Override
    protected List<DataPoint> filterDataPointByTimeRange(List<Range<Instant>> validTimeRanges, List<DataPoint> dataBulk) {
        return dataBulk; //NOTE: filtering on GraminDataPoint level is sufficient
    }

    private GarminTimeData<GarminSleepData> dataToSleep(GarminDataPoint dataPoint) {
        var sleepData = MapperUtils.convertValue(dataPoint, GarminSleepData.class);
        var range = super.getGarminDataPointTimeRange(dataPoint);
        // Documentation describes the total duration consists of `duration` + `awakeDurationInSeconds` + `unmeasurableSleepInSeconds`
        long awakeDurationInSeconds = sleepData.awakeDurationInSeconds() == null ? 0L : sleepData.awakeDurationInSeconds();
        long unmeasurableSleepInSeconds = sleepData.unmeasurableSleepInSeconds() == null ? 0L : sleepData.unmeasurableSleepInSeconds();
        Instant end = range.getMaximum().plusSeconds(awakeDurationInSeconds + unmeasurableSleepInSeconds);
        Range<Instant> newRange = Range.of(range.getMinimum(), end);
        return new GarminTimeData<>(end, sleepData, newRange);
    }
}
