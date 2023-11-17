/*
 * Copyright LBI-DHP and/or licensed to LBI-DHP under one or more
 * contributor license agreements (LBI-DHP: Ludwig Boltzmann Institute
 * for Digital Health and Prevention -- A research institute of the
 * Ludwig Boltzmann Gesellschaft, Oesterreichische Vereinigung zur
 * Foerderung der wissenschaftlichen Forschung).
 * Licensed under the Elastic License 2.0.
 */
package io.redlink.more.data.service;

import io.redlink.more.data.exception.BadRequestException;
import io.redlink.more.data.exception.NotFoundException;
import io.redlink.more.data.model.ApiRoutingInfo;
import io.redlink.more.data.model.Event;
import io.redlink.more.data.repository.StudyRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
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
            throw NotFoundException.Participant(participantId);
        }
        OptionalInt observationStudyGroup = routingInfo.studyGroupId();
        OptionalInt participantStudyGroup = participantOptional.get();

        if(observationStudyGroup.isPresent() && participantStudyGroup.isPresent() && observationStudyGroup.getAsInt() != participantStudyGroup.getAsInt()){
            throw BadRequestException.StudyGroup(observationStudyGroup.getAsInt(), participantStudyGroup.getAsInt());
        }
        return routingInfo.withParticipantStudyGroup(participantStudyGroup);
    }

    public void validateTimeFrame(Long studyId, Integer observationId, List<Instant> timestamps) {
        Optional<Event> schedule = repository.getObservationSchedule(studyId, observationId);
        if(schedule.isEmpty()){
            throw NotFoundException.Observation(observationId);
        }
        Instant startDate = schedule.get().getDateStart();
        Instant endDate = schedule.get().getDateEnd();

        timestamps.forEach(timestamp -> {
            if(timestamp.isBefore(startDate) || timestamp.isAfter(endDate))
                throw BadRequestException.TimeFrame();
        });
    }
}
