/*
 * Copyright LBI-DHP and/or licensed to LBI-DHP under one or more
 * contributor license agreements (LBI-DHP: Ludwig Boltzmann Institute
 * for Digital Health and Prevention -- A research institute of the
 * Ludwig Boltzmann Gesellschaft, Oesterreichische Vereinigung zur
 * Foerderung der wissenschaftlichen Forschung).
 * Licensed under the Elastic License 2.0.
 */
package io.redlink.more.data.service;

import io.redlink.more.data.api.app.v1.model.EndpointDataBulkDTO;
import io.redlink.more.data.api.app.v1.model.ExternalDataDTO;
import io.redlink.more.data.configuration.CachingConfiguration;
import io.redlink.more.data.exception.BadRequestException;
import io.redlink.more.data.exception.NotFoundException;
import io.redlink.more.data.exception.TimeFrameException;
import io.redlink.more.data.model.ApiRoutingInfo;
import io.redlink.more.data.model.Participant;
import io.redlink.more.data.model.RoutingInfo;
import io.redlink.more.data.model.scheduler.Event;
import io.redlink.more.data.model.scheduler.Interval;
import io.redlink.more.data.model.scheduler.RelativeEvent;
import io.redlink.more.data.model.scheduler.ScheduleEvent;
import io.redlink.more.data.repository.StudyRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.Stream;

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
        try {
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
        } catch (Exception e) {
            throw new AccessDeniedException("Invalid token");
        }
    }

    public RoutingInfo validateAndCreateRoutingInfo(ApiRoutingInfo apiRoutingInfo, Integer participantId) {
        RoutingInfo routingInfo = repository.getRoutingInfo(apiRoutingInfo.studyId(), participantId)
                .orElseThrow(() -> NotFoundException.Participant(participantId));

        OptionalInt observationStudyGroup = apiRoutingInfo.studyGroupId();
        OptionalInt participantStudyGroup = routingInfo.studyGroupId();

        if (observationStudyGroup.isPresent() && participantStudyGroup.isPresent() && observationStudyGroup.getAsInt() != participantStudyGroup.getAsInt()) {
            throw BadRequestException.StudyGroup(observationStudyGroup.getAsInt(), participantStudyGroup.getAsInt());
        }
        return routingInfo;
    }

    @Cacheable(CachingConfiguration.OBSERVATION_ENDINGS)
    public void assertTimestampsInBulk(Long studyId, Integer observationId, Integer participantId, EndpointDataBulkDTO dataBulkDTO) {
        Stream<ScheduleEvent> scheduleEvents = Optional.ofNullable(repository.getObservationSchedule(studyId, observationId))
                .orElseThrow(() -> BadRequestException.NotFound(studyId, observationId));

        List<Interval> intervalList = scheduleEvents
                .flatMap(scheduleEvent -> {
                    if (scheduleEvent instanceof Event) {
                        return Stream.of(Interval.from((Event) scheduleEvent));
                    } else if (scheduleEvent instanceof RelativeEvent) {
                        return repository.getIntervals(studyId, participantId, (RelativeEvent) scheduleEvent).stream();
                    } else {
                        throw new BadRequestException("Unsupported ScheduleEvent type: " + scheduleEvent.getClass());
                    }
                })
                .toList();
        scheduleEvents.close();

        if (intervalList.isEmpty()) {
            throw BadRequestException.NotFound(studyId, observationId);
        }

        boolean allValid = dataBulkDTO.getDataPoints().stream()
                .map(ExternalDataDTO::getTimestamp)
                .allMatch(timestamp -> intervalList.stream()
                        .anyMatch(interval -> interval.contains(timestamp))
                );
        if (!allValid) {
            throw TimeFrameException.InvalidDataPointInterval(dataBulkDTO.getParticipantId(), intervalList);
        }
    }

    public List<Participant> listParticipants(Long studyId, OptionalInt studyGroupId) {
        return repository.listParticipants(studyId, studyGroupId);
    }
}
