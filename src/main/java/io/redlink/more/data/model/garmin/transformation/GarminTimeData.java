package io.redlink.more.data.model.garmin.transformation;

import io.redlink.more.data.util.MapperUtils;
import org.apache.commons.lang3.Range;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static io.redlink.more.data.util.ElasticUtils.Constants.GARMIN_SUMMARY_ID_KEY;

public record GarminTimeData<T>(Instant timestamp, T data, Instant startTime, Instant endTime,
                                Map<String, Object> additionalData) {
    private static final String START_TIME_KEY = "startTime";
    private static final String END_TIME_KEY = "endTime";

    public GarminTimeData(Instant timestamp, T data) {
        this(timestamp, data, null, null, Collections.emptyMap());
    }

    public GarminTimeData(Instant timestamp, T data, Range<Instant> timeRange) {
        this(timestamp, data, timeRange.getMinimum(), timeRange.getMaximum(), Collections.emptyMap());
    }

    public GarminTimeData(Instant timestamp, T data, Range<Instant> timeRange, Map<String, Object> additionalData) {
        this(timestamp, data, timeRange.getMinimum(), timeRange.getMaximum(), additionalData);
    }


    public Map<String, Object> dataToMap(String dataKey, String summaryId) {
        HashMap<String, Object> map = new HashMap<>(additionalData);
        if (summaryId != null && !summaryId.isEmpty()) {
            map.put(GARMIN_SUMMARY_ID_KEY, summaryId);
        }
        if (data == null) {
            return map;
        }
        if (MapperUtils.isPrimitiveLike(data)) {
            map.put(dataKey, data);
        } else {
            map.putAll(MapperUtils.convertValue(data, Map.class));
        }

        if (startTime != null) {
            map.put(START_TIME_KEY, startTime);
        }
        if (endTime != null) {
            map.put(END_TIME_KEY, endTime);
        }

        return map.entrySet().stream()
                .filter(e -> e.getValue() != null)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}