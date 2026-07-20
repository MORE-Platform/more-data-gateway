package io.redlink.more.data.service;

import io.redlink.more.data.controller.transformer.StudyTransformer;
import io.redlink.more.data.model.ParticipantObservationSeed;
import io.redlink.more.data.model.RoutingInfo;
import io.redlink.more.data.model.RoutingInfoWithObservation;
import io.redlink.more.data.model.Study;
import io.redlink.more.data.repository.StudyRepository;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
public class StudyService {
    private final StudyRepository studyRepository;

    public StudyService(StudyRepository studyRepository) {
        this.studyRepository = studyRepository;
    }

    /**
     * Provides the full {@link RoutingInfo} for the parsed study participant reference
     *
     * @param studyId       the study id of the participant
     * @param participantId the id of the participant within the study
     * @return the {@link RoutingInfo} if the referenced study participant existed
     */
    public Optional<RoutingInfo> getRoutingInfo(long studyId, int participantId) {
        return studyRepository.getRoutingInfo(studyId, participantId);
    }

    public Optional<RoutingInfoWithObservation> getRoutingInfoByToken(long studyId, String token) {
//        return studyRepository.getRoutingInfoAndObservationIdByToken(studyId, token); --> only if limesurveys are not cross study
        return studyRepository.getRoutingInfoAndObservationIdByToken(token); // Use cross study functionality with mcuh higher runtime
    }

    public Optional<String> getStudyState(long studyId) {
        return studyRepository.getStudyState(studyId);
    }

    public Optional<Pair<Study, List<ParticipantObservationSeed>>> getStudy(RoutingInfo routingInfo) {
        if (routingInfo == null) {
            return Optional.empty();
        }
        Optional<Study> study = studyRepository.findStudy(routingInfo);
        return study
                .map(value -> Pair.of(
                        value,
                        value.active()
                                ? getParticipantObservationSeeds(routingInfo.studyId(), routingInfo.participantId())
                                : Collections.emptyList()))
                ;
    }


    public List<ParticipantObservationSeed> getParticipantObservationSeeds(Long studyId, Integer participantId) {
        var properties = studyRepository.getAllParticpantObservationProperties(studyId, participantId);
        if (properties == null || properties.isEmpty()) {
            return Collections.emptyList();
        }
        return properties
                .stream()
                .map(StudyTransformer::toParticipantObservationSeed)
                .filter(seed -> seed.seed() != null)
                .toList();
    }
}
