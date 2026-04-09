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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static final Logger LOG = LoggerFactory.getLogger(ElasticDataPoint.class);

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
                LOG.info("Exploding polar360 data point {}: keys={}", dataPoint.datapointId(), dataPoint.data().keySet());
                Instant POLAR_EPOCH = Instant.parse("2000-01-01T00:00:00Z");
                List<Map<String, Object>> hrRaw =(List<Map<String, Object>>) dataPoint.data().get("polar360hrdata");
                if(hrRaw != null && !hrRaw.isEmpty()){
                        LOG.info("polar360 HR raw data: {} samples: {}", hrRaw.size(), hrRaw);

                        List<HrData> hrList = hrRaw.stream()
                                .filter(m -> m.get("timestamp") != null && m.get("hr") != null)
                                .map(m -> {
                                    long nanos = ((Number) m.get("timestamp")).longValue();
                                    Instant ts = POLAR_EPOCH.plusNanos(nanos);
                                    return new HrData(ts, ((Number) m.get("hr")).intValue(),(Boolean) m.get("skinContact"));
                                })
                                .toList();
                        LOG.info("polar360 HR after filter: {}/{} samples", hrList.size(), hrRaw.size());

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
                        LOG.info("polar360 ACC raw data: {} samples: {}", accRaw.size(), accRaw);

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
                        LOG.info("polar360 ACC after filter: {}/{} samples", accList.size(), accRaw.size());

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
                        LOG.info("polar360 Temp raw data: {} samples: {}", tempRaw.size(), tempRaw);

                        List<TempData> tempList = tempRaw.stream()
                                .filter(m -> m.get("timestamp") != null && m.get("temp") != null)
                                .map(m -> {
                                    long nanos = ((Number) m.get("timestamp")).longValue();
                                    Instant ts = POLAR_EPOCH.plusNanos(nanos);

                                    return new TempData(((Number) m.get("temp")).floatValue(), ts);
                                })
                                .toList();
                        LOG.info("polar360 Temp after filter: {}/{} samples", tempList.size(), tempRaw.size());

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
                        LOG.info("polar360 PPI raw data: {} samples: {}", ppiRaw.size(), ppiRaw);

                         List<PpiData> ppiList = ppiRaw.stream()
                                .filter(m -> m.get("timestamp") != null && m.get("hr") != null && m.get("ppiInMs") != null && m.get("ppiErrorEstimate") != null && m.get("skinContact") != null)
                                .map(m -> {
                                    long nanos = ((Number) m.get("timestamp")).longValue();
                                    Instant ts = POLAR_EPOCH.plusNanos(nanos);
                                    Object sc = m.get("skinContact");
                                    Boolean skinContact = sc instanceof Boolean b ? b : sc instanceof Number n ? n.intValue() != 0 : null;

                                    return new PpiData(
                                            ((Number) m.get("hr")).intValue(),
                                            ts,
                                            ((Number) m.get("ppiInMs")).intValue(),
                                            ((Number) m.get("ppiErrorEstimate")).intValue(),
                                            skinContact
                                    );
                                })
                                .toList();
                        LOG.info("polar360 PPI after filter: {}/{} samples", ppiList.size(), ppiRaw.size());

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
                                                "skinContact", Boolean.TRUE.equals(p.skinContact)
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
                                                "skinContact", Boolean.TRUE.equals(p.skinContact)
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