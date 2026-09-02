/*
 * Copyright (c) 2022 Redlink GmbH.
 */
package io.redlink.more.data.controller.transformer;

import io.redlink.more.data.api.app.v1.model.ContactInfoDTO;
import io.redlink.more.data.api.app.v1.model.ObservationDTO;
import io.redlink.more.data.api.app.v1.model.ObservationScheduleDTO;
import io.redlink.more.data.api.app.v1.model.SimpleParticipantDTO;
import io.redlink.more.data.api.app.v1.model.StudyDTO;
import io.redlink.more.data.model.CompletedData;
import io.redlink.more.data.model.Contact;
import io.redlink.more.data.model.Observation;
import io.redlink.more.data.model.ParticipantObservationSeed;
import io.redlink.more.data.model.ParticipantWithObservationProperties;
import io.redlink.more.data.model.SimpleParticipant;
import io.redlink.more.data.model.Study;
import io.redlink.more.data.util.RandomSchedulerUtils;
import io.redlink.more.data.util.SchedulerUtils;
import org.apache.commons.lang3.Range;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public final class StudyTransformer {

    private StudyTransformer() {
    }

    public static StudyDTO toDTO(Study study, List<ParticipantObservationSeed> seeds, List<CompletedData> completedData,
                                  Function<Integer, Optional<Instant>> milestoneAnchor) {
        return new StudyDTO()
                .active(study.active())
                .studyTitle(study.title())
                .participantInfo(study.participantInfo())
                .consentInfo(study.consentInfo())
                .finishText(study.finishText())
                .studyState(toStudyStateDTO(study.studyState()))
                .participant(toDTO(study.participant()))
                .contact(toDTO(study.contact()))
                .start(study.startDate())
                .end(study.endDate())
                .observations(toDTO(seeds, study.observations(), study.participant().start(), study.participant().end(), completedData, milestoneAnchor))
                .version(BaseTransformers.toVersionTag(study.modified()))
                ;
    }

    private static StudyDTO.StudyStateEnum toStudyStateDTO(String studyState) {
        return switch (studyState) {
            case "active", "preview" -> StudyDTO.StudyStateEnum.ACTIVE;
            case "paused", "paused-preview" -> StudyDTO.StudyStateEnum.PAUSED;
            default -> StudyDTO.StudyStateEnum.CLOSED;
        };
    }

    public static SimpleParticipantDTO toDTO(SimpleParticipant participant) {
        if (participant == null) {
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

    public static List<ObservationDTO> toDTO(List<ParticipantObservationSeed> participantObservationSeeds, List<Observation> observations, Instant start, Instant end, List<CompletedData> completedData,
                                              Function<Integer, Optional<Instant>> milestoneAnchor) {
        return observations.stream().map(o -> {
            var seed = participantObservationSeeds.stream()
                    .filter(s -> s.observationId() == o.observationId())
                    .findFirst()
                    .orElse(null);
            return StudyTransformer.toDTO(seed, o, start, end, completedData.stream().filter(d -> d.observationId().equals(String.valueOf(o.observationId()))).toList(), milestoneAnchor);
        }).toList();
    }

    public static ObservationDTO toDTO(ParticipantObservationSeed participantObservationSeed, Observation observation, Instant start, Instant end, List<CompletedData> completedData,
                                        Function<Integer, Optional<Instant>> milestoneAnchor) {
        ObservationDTO dto = new ObservationDTO()
                .observationId(String.valueOf(observation.observationId()))
                .observationType(observation.type())
                .observationTitle(observation.title())
                .participantInfo(observation.participantInfo())
                ._configuration(observation.properties())
                .version(BaseTransformers.toVersionTag(observation.modified()))
                .hidden(observation.hidden())
                .noSchedule(observation.noSchedule())
                .reminder(observation.reminder());

        Instant anchor = start;
        boolean isMilestoneAnchor = false;
        if (observation.milestoneId() != null) {
            Optional<Instant> resolved = milestoneAnchor.apply(observation.milestoneId());
            anchor = resolved.orElse(null);
            isMilestoneAnchor = resolved.isPresent();
        }

        if (observation.observationSchedule() != null && anchor != null) {
            final Instant finalAnchor = anchor;
            dto.schedule(SchedulerUtils
                    .parseToObservationSchedules(
                            participantObservationSeed,
                            observation.observationSchedule(),
                            anchor,
                            end,
                            isMilestoneAnchor)
                    .stream()
                    .map(StudyTransformer::toObservationScheduleDTO)
                    .filter(schedule -> completedData.stream().noneMatch(d -> {
                        Instant scheduleStart = schedule.getStart() != null ? schedule.getStart() : finalAnchor;
                        Instant scheduleEnd = schedule.getEnd() != null ? schedule.getEnd() : end;

                        return d.scheduleStart().compareTo(scheduleStart) == 0
                                && d.scheduleEnd().compareTo(scheduleEnd) == 0;
                    }))
                    .toList());
        }
        return dto;
    }

    public static ObservationScheduleDTO toObservationScheduleDTO(Range<Instant> schedule) {
        Instant instant = schedule.getMaximum();
        Instant instant1 = schedule.getMinimum();
        return new ObservationScheduleDTO()
                .start(instant1)
                .end(instant)
                ;
    }

    public static ParticipantObservationSeed toParticipantObservationSeed(ParticipantWithObservationProperties participantWithObservationProperties) {
        return new ParticipantObservationSeed(
                participantWithObservationProperties.studyId(),
                participantWithObservationProperties.participantId(),
                participantWithObservationProperties.observationId(),
                (Long) participantWithObservationProperties.properties().getOrDefault(RandomSchedulerUtils.OBSERVATION_SCHEDULE_SEED_KEY, null));
    }
}
