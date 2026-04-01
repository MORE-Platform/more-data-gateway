/*
 * Copyright LBI-DHP and/or licensed to LBI-DHP under one or more
 * contributor license agreements (LBI-DHP: Ludwig Boltzmann Institute
 * for Digital Health and Prevention -- A research institute of the
 * Ludwig Boltzmann Gesellschaft, Oesterreichische Vereinigung zur
 * Foerderung der wissenschaftlichen Forschung).
 * Licensed under the Elastic License 2.0.
 */
package io.redlink.more.data.elastic.model;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import io.netty.util.internal.EmptyArrays;
import io.redlink.more.data.model.DataPoint;
import io.redlink.more.data.model.RoutingInfo;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.UUID;

import org.apache.velocity.tools.config.Data;

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
                //TODO: Do we need observation groups in the Elastic index. I am not sure (westei, 18.12.2025)
                dataPoint.observationId(),
                dataPoint.observationType(),
                dataPoint.dataType(),
                dataPoint.serverTime(),
                dataPoint.effectiveDateTime(),
                dataPoint.data()
        );
    }

    private record HrData(Instant timestamp, int hr, Boolean skinContact){}
    private record AccData(int x, int y, int z , Instant timestamp){}
    private record PpiData(int hr, Instant timestamp,int ppiInMs, int ppiErrorEstimate, Boolean skinContact){}
    private record TempData(float temp,Instant timestamp){}

    public static List<ElasticDataPoint> explode_toElastic(DataPoint dataPoint,RoutingInfo elasticInfo){
        List<ElasticDataPoint> items = new ArrayList<ElasticDataPoint>();
        Boolean idcheck_set = false;
        if(dataPoint.data().keySet().stream().anyMatch(key -> key.toLowerCase().contains("polar360")))
        {
                Instant POLAR_EPOCH = Instant.parse("2000-01-01T00:00:00Z");
                List<Map<String, Object>> hrRaw =(List<Map<String, Object>>) dataPoint.data().get("polar360hrdata");
                if(hrRaw != null && !hrRaw.isEmpty()){

                        List<HrData> hrList = hrRaw.stream()
                                .filter(m -> m.get("timestamp") != null && m.get("hr") != null)
                                .map(m -> {
                                    long nanos = ((Number) m.get("timestamp")).longValue();
                                    Instant ts = POLAR_EPOCH.plusNanos(nanos);
                                    return new HrData(ts, ((Number) m.get("hr")).intValue(),(Boolean) m.get("skinContact"));
                                })
                                .toList();
                                  
                                
                        for(HrData h : hrList){
                                if(!idcheck_set){
                                        items.add(
                                        new ElasticDataPoint(
                                        dataPoint.datapointId() ,
                                        "participant_%d".formatted(elasticInfo.participantId()),
                                        "study_%d".formatted(elasticInfo.studyId()),
                                        elasticInfo.studyGroupId().stream()
                                                .mapToObj("study_group_%d"::formatted)
                                                .findFirst()
                                                .orElse(null),
                                        dataPoint.observationId(),
                                        dataPoint.observationType(),
                                        dataPoint.dataType(),
                                        h.timestamp(),
                                        h.timestamp(),
                                        Map.of(
                                                "hr",h.hr,
                                                "skinContact" ,h.skinContact
                                        )
                                        )
                                                );

                                        idcheck_set = true;
                                }
                                else{
                                        items.add(
                                        new ElasticDataPoint(
                                        dataPoint.datapointId() + UUID.randomUUID().toString(),
                                        "participant_%d".formatted(elasticInfo.participantId()),
                                        "study_%d".formatted(elasticInfo.studyId()),
                                        elasticInfo.studyGroupId().stream()
                                                .mapToObj("study_group_%d"::formatted)
                                                .findFirst()
                                                .orElse(null),
                                        dataPoint.observationId(),
                                        dataPoint.observationType(),
                                        dataPoint.dataType(),
                                        h.timestamp(),
                                        h.timestamp(),
                                        Map.of(
                                                "hr",h.hr,
                                                "skinContact" ,h.skinContact
                                        )
                                        )
                                                );
                                }
                                
                        }


                }

                List<Map<String, Object>> accRaw = (List<Map<String, Object>>) dataPoint.data().get("polar360accdata");
                if (accRaw!= null && !accRaw.isEmpty()) {

                        List<AccData> accList = accRaw.stream()
                                 .filter(m -> m.get("timestamp") != null && m.get("x") != null && m.get("y") != null && m.get("z") != null)
                                 .map(m -> {
                                     long nanos = ((Number) m.get("timestamp")).longValue();
                                     Instant ts = POLAR_EPOCH.plusNanos(nanos);
                                     return new AccData(
                                             ((Number) m.get("x")).intValue(),
                                             ((Number) m.get("y")).intValue(),
                                             ((Number) m.get("z")).intValue(),
                                             ts
                                     );
                                 })
                                 .toList();
                        for(AccData a : accList){
                                if (!idcheck_set) {
                                        items.add(
                                        new ElasticDataPoint(
                                        dataPoint.datapointId() ,
                                        "participant_%d".formatted(elasticInfo.participantId()),
                                        "study_%d".formatted(elasticInfo.studyId()),
                                        elasticInfo.studyGroupId().stream()
                                                .mapToObj("study_group_%d"::formatted)
                                                .findFirst()
                                                .orElse(null),
                                        dataPoint.observationId(),
                                        dataPoint.observationType(),
                                        dataPoint.dataType(),
                                        a.timestamp(),
                                        a.timestamp(),
                                        Map.of(
                                                "x", a.x,
                                                "y", a.y,
                                                "z",a.z
                                        )
                                        )
                                );
                                        idcheck_set=true;
                                }
                                else{
                                        items.add(
                                        new ElasticDataPoint(
                                        dataPoint.datapointId() + UUID.randomUUID().toString(),
                                        "participant_%d".formatted(elasticInfo.participantId()),
                                        "study_%d".formatted(elasticInfo.studyId()),
                                        elasticInfo.studyGroupId().stream()
                                                .mapToObj("study_group_%d"::formatted)
                                                .findFirst()
                                                .orElse(null),
                                        dataPoint.observationId(),
                                        dataPoint.observationType(),
                                        dataPoint.dataType(),
                                        a.timestamp(),
                                        a.timestamp(),
                                        Map.of(
                                                "x", a.x,
                                                "y", a.y,
                                                "z",a.z
                                        )
                                        )
                                );
                                }
                                
                        }
                        
                }
                List<Map<String, Object>> tempRaw =(List<Map<String, Object>>) dataPoint.data().get("polar360tempdata");
                if (tempRaw!=null && !tempRaw.isEmpty()) {
                        List<TempData> tempList = tempRaw.stream()
                                .filter(m -> m.get("timestamp") != null && m.get("temp") != null)
                                .map(m -> {
                                    long nanos = ((Number) m.get("timestamp")).longValue();
                                    Instant ts = POLAR_EPOCH.plusNanos(nanos);

                                    return new TempData(((Number) m.get("temp")).floatValue(), ts);
                                })
                                .toList();


                        for(TempData t : tempList){
                                if (!idcheck_set) {
                                        items.add(
                                        new ElasticDataPoint(
                                        dataPoint.datapointId() ,
                                        "participant_%d".formatted(elasticInfo.participantId()),
                                        "study_%d".formatted(elasticInfo.studyId()),
                                        elasticInfo.studyGroupId().stream()
                                                .mapToObj("study_group_%d"::formatted)
                                                .findFirst()
                                                .orElse(null),
                                        dataPoint.observationId(),
                                        dataPoint.observationType(),
                                        dataPoint.dataType(),
                                        t.timestamp(),
                                        t.timestamp(),
                                        Map.of(
                                               "temperature",t.temp
                                        )
                                        )
                                ); 
                                        idcheck_set= true;
                                }
                                else{
                                         items.add(
                                        new ElasticDataPoint(
                                        dataPoint.datapointId() + UUID.randomUUID().toString(),
                                        "participant_%d".formatted(elasticInfo.participantId()),
                                        "study_%d".formatted(elasticInfo.studyId()),
                                        elasticInfo.studyGroupId().stream()
                                                .mapToObj("study_group_%d"::formatted)
                                                .findFirst()
                                                .orElse(null),
                                        dataPoint.observationId(),
                                        dataPoint.observationType(),
                                        dataPoint.dataType(),
                                        t.timestamp(),
                                        t.timestamp(),
                                        Map.of(
                                               "temperature",t.temp
                                        )
                                        )
                                );
                                }
                               
                        }
                }
                List<Map<String, Object>> ppiRaw =(List<Map<String, Object>>) dataPoint.data().get("polar360ppidata");
                if (ppiRaw!= null && !ppiRaw.isEmpty()) {
                         List<PpiData> ppiList = ppiRaw.stream()
                                .filter(m -> m.get("timestamp") != null && m.get("hr") != null && m.get("ppiInMs") != null && m.get("ppiErrorEstimate") != null)
                                .map(m -> {
                                    long nanos = ((Number) m.get("timestamp")).longValue();
                                    Instant ts = POLAR_EPOCH.plusNanos(nanos);

                                    return new PpiData(
                                            ((Number) m.get("hr")).intValue(),
                                            ts,
                                            ((Number) m.get("ppiInMs")).intValue(),
                                            ((Number) m.get("ppiErrorEstimate")).intValue(),
                                            ((Boolean)m.get("skinContact"))
                                    );
                                })
                                .toList();
                        
                        for(PpiData p : ppiList){
                                if(!idcheck_set){
                                        items.add(
                                        new ElasticDataPoint(
                                        dataPoint.datapointId() ,
                                        "participant_%d".formatted(elasticInfo.participantId()),
                                        "study_%d".formatted(elasticInfo.studyId()),
                                        elasticInfo.studyGroupId().stream()
                                                .mapToObj("study_group_%d"::formatted)
                                                .findFirst()
                                                .orElse(null),
                                        dataPoint.observationId(),
                                        dataPoint.observationType(),
                                        dataPoint.dataType(),
                                        p.timestamp(),
                                        p.timestamp(),
                                        Map.of(
                                               "hr", p.hr,
                                                "ppiInMs",p.ppiInMs,
                                                "ppiErrorEstimate",p.ppiErrorEstimate,
                                                "skinContact" ,p.skinContact
                                        )
                                        )
                                        );
                                        idcheck_set=true;
                                }
                                else{
                                        items.add(
                                        new ElasticDataPoint(
                                        dataPoint.datapointId() + UUID.randomUUID().toString(),
                                        "participant_%d".formatted(elasticInfo.participantId()),
                                        "study_%d".formatted(elasticInfo.studyId()),
                                        elasticInfo.studyGroupId().stream()
                                                .mapToObj("study_group_%d"::formatted)
                                                .findFirst()
                                                .orElse(null),
                                        dataPoint.observationId(),
                                        dataPoint.observationType(),
                                        dataPoint.dataType(),
                                        p.timestamp(),
                                        p.timestamp(),
                                        Map.of(
                                               "hr", p.hr,
                                                "ppiInMs",p.ppiInMs,
                                                "ppiErrorEstimate",p.ppiErrorEstimate,
                                                "skinContact",p.skinContact
                                        )
                                        )
                                );
                                }
                                
                        }
                }



        }



        return items;
    }
}