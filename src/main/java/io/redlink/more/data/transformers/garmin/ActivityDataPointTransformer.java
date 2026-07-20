package io.redlink.more.data.transformers.garmin;

import io.redlink.more.data.custom.model.GarminDataPoint;
import io.redlink.more.data.model.DataPoint;
import io.redlink.more.data.model.DataType;
import io.redlink.more.data.model.Observation;
import io.redlink.more.data.model.garmin.GarminSummaryType;
import io.redlink.more.data.model.garmin.transformation.GarminActivityModel;
import io.redlink.more.data.model.garmin.transformation.GarminTimeData;
import org.apache.commons.lang3.Range;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

@Component
public class ActivityDataPointTransformer extends AbstractGarminTransformer {

    @Override
    public GarminSummaryType getSupportedType() {
        return GarminSummaryType.EPOCHS;
    }

    @Override
    public String getObservationType() {
        return "garmin-activity-observation";
    }

    @Override
    public List<DataPoint> transformToDataPoint(List<Observation> observations, GarminDataPoint garminDataPoint) {
        if (garminDataPoint.getActivityType() != null
                && garminDataPoint.getMet() != null
                && garminDataPoint.getMet() >= 0
                && garminDataPoint.getIntensity() != null) {
            var data = extractActivityData(garminDataPoint);
            return transformGarminTimeDataToDataPoint(
                    observations,
                    garminDataPoint.getSummaryId(),
                    DataType.ACTIVITY,
                    data
            );
        }
        return Collections.emptyList();
    }

    @Override
    protected List<DataPoint> filterDataPointByTimeRange(List<Range<Instant>> validTimeRanges, List<DataPoint> dataBulk) {
        return dataBulk;
    }


    private GarminTimeData<GarminActivityModel> extractActivityData(GarminDataPoint garminDataPoint) {
        var activityModel = new GarminActivityModel(
                garminDataPoint.getActivityType(),
                garminDataPoint.getMet(),
                garminDataPoint.getIntensity(),
                garminDataPoint.getActiveTimeInSeconds(),
                garminDataPoint.getMeanMotionIntensity(),
                garminDataPoint.getMaxMotionIntensity());
        var range = super.getGarminDataPointTimeRange(garminDataPoint);
        return new GarminTimeData<>(
                range.getMaximum(),
                activityModel,
                range
        );
    }
}
