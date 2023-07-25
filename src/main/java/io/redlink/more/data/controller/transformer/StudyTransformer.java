/*
 * Copyright (c) 2022 Redlink GmbH.
 */
package io.redlink.more.data.controller.transformer;

import io.redlink.more.data.api.app.v1.model.*;
import io.redlink.more.data.model.Contact;
import io.redlink.more.data.model.Observation;
import io.redlink.more.data.model.SimpleParticipant;
import io.redlink.more.data.model.Study;
import io.redlink.more.data.schedule.ICalendarParser;
import org.apache.commons.lang3.tuple.Pair;

import java.time.Instant;
import java.util.List;

public final class StudyTransformer {

    private StudyTransformer() {}

    public static StudyDTO toDTO(Study study) {
        return new StudyDTO()
                .active(study.active())
                .studyTitle(study.title())
                .participantInfo(study.participantInfo())
                .consentInfo(study.consentInfo())
                .finishText(study.finishText())
                .studyState(StudyDTO.StudyStateEnum.fromValue(study.studyState()))
                .participant(toDTO(study.participant()))
                .contact(toDTO(study.contact()))
                .start(study.startDate())
                .end(study.endDate())
                .observations(toDTO(study.observations()))
                .version(BaseTransformers.toVersionTag(study.modified()))
                ;
    }

    public static SimpleParticipantDTO toDTO(SimpleParticipant participant) {
        if(participant == null) {
            return null;
        }
        return new SimpleParticipantDTO()
                .id(participant.id())
                .alias(participant.alias());
    }

    public static ContactInfoDTO toDTO(Contact contact) {
        return new ContactInfoDTO()
                .institute(contact.institute())
                .person(contact.person())
                .email(contact.email())
                .phoneNumber(contact.phoneNumber())
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
                ._configuration(observation.properties())
                .version(BaseTransformers.toVersionTag(observation.modified()))
                .hidden(observation.hidden())
                ;
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
                .start(BaseTransformers.toOffsetDateTime(schedule.getLeft()))
                .end(BaseTransformers.toOffsetDateTime(schedule.getRight()))
                ;
    }

}
