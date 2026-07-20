package io.redlink.more.data.transformers.garmin;

import io.redlink.more.data.custom.model.GarminDataPoint;
import io.redlink.more.data.model.DataPoint;
import io.redlink.more.data.model.DataType;
import io.redlink.more.data.model.Observation;
import io.redlink.more.data.model.garmin.GarminSummaryType;
import io.redlink.more.data.model.garmin.transformation.GarminBloodPressure;
import io.redlink.more.data.model.garmin.transformation.GarminTimeData;
import io.redlink.more.data.util.MapperUtils;
import org.apache.commons.lang3.Range;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class BloodPressureTransformer extends AbstractGarminTransformer {
    public BloodPressureTransformer() {
        super();
    }

    @Override
    public GarminSummaryType getSupportedType() {
        return GarminSummaryType.BLOODPRESSURES;
    }

    @Override
    public String getObservationType() {
        return "garmin-blood-pressure-observation";
    }

    @Override
    protected List<DataPoint> transformToDataPoint(List<Observation> observations, GarminDataPoint garminDataPoint) {
        var data = extractBloodPressure(garminDataPoint);
        return super.transformGarminTimeDataToDataPoint(observations, garminDataPoint.getSummaryId(), DataType.BLOOD_PRESSURE, data);
    }

    @Override
    protected List<DataPoint> filterDataPointByTimeRange(List<Range<Instant>> validTimeRanges, List<DataPoint> dataBulk) {
        return dataBulk;
    }

    private GarminTimeData<GarminBloodPressure> extractBloodPressure(GarminDataPoint garminDataPoint) {
        GarminBloodPressure data = MapperUtils.convertValue(garminDataPoint, GarminBloodPressure.class);
        var measurementTime = super.recordingTimestamp(garminDataPoint);
        return new GarminTimeData<>(measurementTime.toInstant(), data);
    }
}
