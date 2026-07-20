package io.redlink.more.data.controller.transformer;

import io.redlink.more.data.api.participant.v1.model.ContactInfoDTO;
import io.redlink.more.data.api.participant.v1.model.DataHealthStateDTO;
import io.redlink.more.data.api.participant.v1.model.ObservationDTO;
import io.redlink.more.data.api.participant.v1.model.ObservationScheduleDTO;
import io.redlink.more.data.api.participant.v1.model.SimpleParticipantDTO;
import io.redlink.more.data.api.participant.v1.model.StudyDTO;
import io.redlink.more.data.model.Contact;
import io.redlink.more.data.model.DataHealth;
import io.redlink.more.data.model.Observation;
import io.redlink.more.data.model.ParticipantObservationSeed;
import io.redlink.more.data.model.SimpleParticipant;
import io.redlink.more.data.model.Study;
import io.redlink.more.data.util.SchedulerUtils;
import org.apache.commons.lang3.Range;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public final class ParticipantPortalTransformer {

    private static final Logger LOG = LoggerFactory.getLogger(ParticipantPortalTransformer.class);

    public static StudyDTO toDTO(Study study, List<ParticipantObservationSeed> seeds) {
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
                .observations(toDTO(seeds, study.observations(),
                        Optional.ofNullable(study.participant()).map(SimpleParticipant::start).orElse(null),
                        Optional.ofNullable(study.participant()).map(SimpleParticipant::end).orElse(null)))
                .version(BaseTransformers.toVersionTag(study.modified()));
    }

    private static StudyDTO.StudyStateEnum toStudyStateDTO(String studyState) {
        if (studyState == null) {
            return StudyDTO.StudyStateEnum.CLOSED;
        }
        return switch (studyState) {
            case "active" -> StudyDTO.StudyStateEnum.ACTIVE;
            case "preview" -> StudyDTO.StudyStateEnum.PREVIEW;
            case "paused", "paused-preview" -> StudyDTO.StudyStateEnum.PAUSED;
            default -> StudyDTO.StudyStateEnum.CLOSED;
        };
    }

    public static SimpleParticipantDTO toDTO(SimpleParticipant participant) {
        if (participant == null) {
            return null;
        }
        return new SimpleParticipantDTO()
                .alias(participant.alias());
    }

    public static ContactInfoDTO toDTO(Contact contact) {
        if (contact == null) {
            return null;
        }
        return new ContactInfoDTO()
                .institute(contact.institute())
                .person(contact.person())
                .email(contact.email())
                .phoneNumber(contact.phoneNumber());
    }

    public static List<ObservationDTO> toDTO(List<ParticipantObservationSeed> participantObservationSeeds, List<Observation> observations, Instant start, Instant end) {
        return observations.stream().map(o -> {
            var seed = participantObservationSeeds.stream()
                    .filter(s -> s.observationId() == o.observationId())
                    .findFirst()
                    .orElse(null);
            return ParticipantPortalTransformer.toDTO(seed, o, start, end);
        }).toList();
    }

    public static ObservationDTO toDTO(ParticipantObservationSeed participantObservationSeed, Observation observation, Instant start, Instant end) {
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
        if (observation.observationSchedule() != null && start != null) {
            dto.schedule(SchedulerUtils
                    .parseToObservationSchedules(
                            participantObservationSeed,
                            observation.observationSchedule(),
                            start,
                            end)
                    .stream()
                    .map(ParticipantPortalTransformer::toObservationScheduleDTO)
                    .toList());
        }
        return dto;
    }

    public static ObservationScheduleDTO toObservationScheduleDTO(Range<Instant> schedule) {
        return new ObservationScheduleDTO()
                .start(schedule.getMinimum())
                .end(schedule.getMaximum());
    }

    public static DataHealthStateDTO toDataHealStateDto(DataHealth dataHealth) {
        DataHealthStateDTO dataHealhState;
        if (dataHealth == null) {
            dataHealhState = null;
        } else if (!dataHealth.valid()) {
            dataHealhState = DataHealthStateDTO.INVALID;
        } else {
            switch (dataHealth.state()) {
                case PARTIAL, INCOMPLETE -> dataHealhState = DataHealthStateDTO.INCOMPLETE;
                case COMPLETE -> dataHealhState = DataHealthStateDTO.COMPLETED;
                default -> {
                    try {
                        dataHealhState = DataHealthStateDTO.fromValue(dataHealth.state().getValue());
                    } catch (IllegalArgumentException e) {
                        LOG.error("Unexpected data health state {}", dataHealth.state(), e);
                        dataHealhState = null;
                    }
                }
            }
        }
        return dataHealhState;
    }
}
