package io.redlink.more.data.transformers.garmin.steps;

import io.redlink.more.data.custom.model.GarminDataPoint;
import io.redlink.more.data.model.DataPoint;
import io.redlink.more.data.model.DataType;
import io.redlink.more.data.model.Observation;
import io.redlink.more.data.model.garmin.GarminSummaryType;
import io.redlink.more.data.transformers.garmin.AbstractGarminTransformer;
import io.redlink.more.data.util.garmin.GarminStepDataUtils;
import org.apache.commons.lang3.Range;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class DailyStepDataTransformer extends AbstractGarminTransformer {
    public DailyStepDataTransformer() {
        super();
    }

    @Override
    public GarminSummaryType getSupportedType() {
        return GarminSummaryType.DAILIES;
    }

    @Override
    public String getObservationType() {
        return "garmin-daily-steps-observation";
    }

    @Override
    protected List<DataPoint> transformToDataPoint(List<Observation> observations, GarminDataPoint garminDataPoint) {
        var data = GarminStepDataUtils.getStepData(super.recordingTimestamp(garminDataPoint).toInstant(), garminDataPoint);
        return super.transformGarminTimeDataToDataPoint(observations, garminDataPoint.getSummaryId(), DataType.DAILY_STEPS, data);
    }

    @Override
    protected List<DataPoint> filterDataPointByTimeRange(List<Range<Instant>> validTimeRanges, List<DataPoint> dataBulk) {
        return dataBulk;
    }
}
