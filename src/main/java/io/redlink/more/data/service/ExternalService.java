/*
 * Copyright LBI-DHP and/or licensed to LBI-DHP under one or more
 * contributor license agreements (LBI-DHP: Ludwig Boltzmann Institute
 * for Digital Health and Prevention -- A research institute of the
 * Ludwig Boltzmann Gesellschaft, Oesterreichische Vereinigung zur
 * Foerderung der wissenschaftlichen Forschung).
 * Licensed under the Elastic License 2.0.
 */
package io.redlink.more.data.service;

import io.redlink.more.data.configuration.CachingConfiguration;
import io.redlink.more.data.exception.BadRequestException;
import io.redlink.more.data.exception.NotFoundException;
import io.redlink.more.data.model.ApiRoutingInfo;
import io.redlink.more.data.model.Participant;
import io.redlink.more.data.model.scheduler.Event;
import io.redlink.more.data.model.scheduler.Interval;
import io.redlink.more.data.model.scheduler.RelativeEvent;
import io.redlink.more.data.repository.StudyRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Base64;
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
    public ApiRoutingInfo getRoutingInfo(
            String moreApiToken
    ) {
        String[] split = moreApiToken.split("\\.");
        String[] primaryKey = new String(Base64.getDecoder().decode(split[0])).split("-");

        Long studyId = Long.valueOf(primaryKey[0]);
        Integer observationId = Integer.valueOf(primaryKey[1]);
        Integer tokenId = Integer.valueOf(primaryKey[2]);
        String secret = new String(Base64.getDecoder().decode(split[1]));


        final Optional<ApiRoutingInfo> apiRoutingInfo = repository.getApiRoutingInfo(studyId, observationId, tokenId)
                .stream().filter(route ->
                        passwordEncoder.matches(secret, route.secret()))
                .findFirst();
        if (apiRoutingInfo.isEmpty()) {
            throw new AccessDeniedException("Invalid token");
        }
        return apiRoutingInfo.get();
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

    @Cacheable(CachingConfiguration.OBSERVATION_ENDINGS)
    public Interval getIntervalForObservation(Long studyId, Integer observationId, Integer participantId) {
        return repository.getObservationSchedule(studyId, observationId)
                .map(scheduleEvent -> {
                    if(Event.class.isAssignableFrom(scheduleEvent.getClass())) {
                        return Interval.from((Event) scheduleEvent);
                    } else {
                        return repository.getInterval(studyId, participantId, (RelativeEvent) scheduleEvent);
                    }
                })
                .orElseThrow(BadRequestException::TimeFrame);
    }

    public List<Participant> listParticipants(Long studyId, OptionalInt studyGroupId) {
        return repository.listParticipants(studyId, studyGroupId.orElse(Integer.MIN_VALUE));
    }
}
