/*
 * Copyright (c) 2022 Redlink GmbH.
 */
package io.redlink.more.data.controller.transformer;

import io.redlink.more.data.api.app.v1.model.*;
import io.redlink.more.data.model.*;
import io.redlink.more.data.schedule.SchedulerUtils;
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
                .observations(toDTO(study.observations(), study.participant().start(), study.participant().end()))
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

    public static List<ParticipantDTO> toDTO(List<Participant> participants) {
        if(participants == null) {
            return List.of();
        }
        return participants.stream()
                .map(StudyTransformer::toDTO)
                .toList();
    }

    public static ParticipantDTO toDTO(Participant participant) {
        if(participant == null) {
            return null;
        }
        return new ParticipantDTO(
                participant.id(),
                participant.alias(),
                ParticipantStatusDTO.fromValue(participant.status())
        );
    }

    public static ContactInfoDTO toDTO(Contact contact) {
        return new ContactInfoDTO()
                .institute(contact.institute())
                .person(contact.person())
                .email(contact.email())
                .phoneNumber(contact.phoneNumber())
                ;
    }

    public static List<ObservationDTO> toDTO(List<Observation> observations, Instant start, Instant end) {
        return observations.stream().map(o -> StudyTransformer.toDTO(o, start, end)).toList();
    }

    public static ObservationDTO toDTO(Observation observation, Instant start, Instant end) {
        ObservationDTO dto =  new ObservationDTO()
                .observationId(String.valueOf(observation.observationId()))
                .observationType(observation.type())
                .observationTitle(observation.title())
                .participantInfo(observation.participantInfo())
                ._configuration(observation.properties())
                .version(BaseTransformers.toVersionTag(observation.modified()))
                .hidden(observation.hidden())
                .noSchedule(observation.noSchedule())
                ;
       if(observation.observationSchedule() != null && start != null) {
           dto.schedule(SchedulerUtils
                        .parseToObservationSchedules(observation.observationSchedule(), start, end)
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
