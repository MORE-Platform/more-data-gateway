/*
 * Copyright (c) 2022 Redlink GmbH.
 */
package io.redlink.more.data.controller.transformer;

import io.redlink.more.data.api.app.v1.model.ObservationDTO;
import io.redlink.more.data.api.app.v1.model.ObservationScheduleDTO;
import io.redlink.more.data.api.app.v1.model.StudyDTO;
import io.redlink.more.data.model.Observation;
import io.redlink.more.data.model.Study;
import io.redlink.more.data.schedule.ICalendarParser;
import org.apache.commons.lang3.tuple.Pair;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

public final class StudyTransformer {

    private StudyTransformer() {}

    public static StudyDTO toDTO(Study study) {
        return new StudyDTO()
                .studyTitle(study.title())
                .participantInfo(study.participantInfo())
                .consentInfo(study.consentInfo())
                .start(study.startDate())
                .end(study.endDate())
                .observations(toDTO(study.observations()))
                ;
    }

    public static List<ObservationDTO> toDTO(List<Observation> observations) {
        return observations.stream().map(StudyTransformer::toDTO).toList();
    }

    public static ObservationDTO toDTO(Observation observation) {
        ObservationDTO dto =  new ObservationDTO()
                .observationId(String.valueOf(observation.observationId()))
                .observationType(observation.type())
                .observationTitle(observation.title())
                .participantInfo(observation.participantInfo())
                ._configuration(observation.properties());
       if(observation.observationSchedule() != null) {
           dto.schedule(ICalendarParser
                        .parseToObservationSchedules(observation.observationSchedule())
                        .stream()
                        .map(StudyTransformer::toObservationScheduleDTO)
                        .toList());
       }
       return dto;
    }

    public static ObservationScheduleDTO toObservationScheduleDTO(Pair<Instant, Instant> schedule) {
        return new ObservationScheduleDTO()
                .start(schedule.getLeft().atOffset(ZoneOffset.UTC))
                .end(schedule.getRight().atOffset(ZoneOffset.UTC));
    }

}
