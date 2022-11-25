package io.redlink.more.data.elastic.model;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.redlink.more.data.model.DataPoint;
import io.redlink.more.data.model.RoutingInfo;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

public record ElasticDataPoint(
        @JsonProperty("datapoint_id")
        String datapointId,
        @JsonProperty("participant_id")
        String participantId,
        @JsonProperty("study_id")
        String studyId,
        @JsonProperty("study_group_id")
        String studyGroupId,
        @JsonProperty("observation_id")
        String observationId,
        @JsonProperty("observation_type")
        String observationType,
        @JsonProperty("data_type")
        String dataType,
        @JsonProperty("storage_date")
        Instant storageDate,
        @JsonProperty("effective_time_frame")
        Instant effectiveTimeFrame,
        @JsonIgnore
        Map<String, Object> data
) {

    private static final String DATA_FIELD_PREFIX = "data_";

    @JsonCreator
    public ElasticDataPoint {
        data = Map.copyOf(data);
    }

    @JsonAnyGetter
    @JsonUnwrapped(prefix = "data_")
    Map<String, Object> dataMap() {
        // This is a dirty hack as @JsonUnwrapped does not work on Maps
        // (https://github.com/FasterXML/jackson-databind/issues/171)
        return data
                .entrySet()
                .stream()
                .collect(Collectors.toUnmodifiableMap(
                        e -> DATA_FIELD_PREFIX + e.getKey(),
                        Map.Entry::getValue)
                );
    }

    public static ElasticDataPoint toElastic(DataPoint dataPoint, RoutingInfo elasticInfo) {
        return new ElasticDataPoint(
                dataPoint.datapointId(),
                "participant_%d".formatted(elasticInfo.participantId()),
                "study_%d".formatted(elasticInfo.studyId()),
                elasticInfo.studyGroupId().stream()
                        .mapToObj("study_group_%d"::formatted)
                        .findFirst()
                        .orElse(null),
                dataPoint.observationId(),
                dataPoint.observationType(),
                dataPoint.dataType(),
                dataPoint.serverTime(),
                dataPoint.effectiveDateTime(),
                dataPoint.data()
        );
    }
}
