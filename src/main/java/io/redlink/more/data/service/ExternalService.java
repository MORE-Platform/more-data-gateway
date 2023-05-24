package io.redlink.more.data.service;

import io.redlink.more.data.model.ApiRoutingInfo;
import io.redlink.more.data.repository.StudyRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.OptionalInt;

@Service
public class ExternalService {
    private final StudyRepository repository;
    private final PasswordEncoder passwordEncoder;

    public ExternalService(StudyRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }
    public Optional<ApiRoutingInfo> getRoutingInfo(
            Long studyId, Integer observationId, Integer tokenId, String apiSecret
    ) {
        return repository.getApiRoutingInfo(studyId, observationId, tokenId)
                .stream().filter(route ->
                        passwordEncoder.matches(apiSecret, route.secret()))
                .findFirst();
    }

    public ApiRoutingInfo validateRoutingInfo(ApiRoutingInfo routingInfo, Integer participantId) {
        Optional<OptionalInt> participantOptional = repository.getParticipantStudyGroupId(routingInfo.studyId(), participantId);
        if(participantOptional.isEmpty()) {
            throw new AccessDeniedException("Participant with given id doesn't exist");
        }
        OptionalInt observationStudyGroup = routingInfo.studyGroupId();
        OptionalInt participantStudyGroup = participantOptional.get();

        if(observationStudyGroup.isPresent() && participantStudyGroup.isPresent() && observationStudyGroup.getAsInt() != participantStudyGroup.getAsInt()){
            throw new AccessDeniedException("Participant doesn't have valid studyGroupId for given observation");
        }
        return routingInfo.withParticipantStudyGroup(participantStudyGroup);
    }
}
