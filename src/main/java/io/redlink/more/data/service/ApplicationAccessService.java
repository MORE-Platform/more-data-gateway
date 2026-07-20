/*
 * Copyright LBI-DHP and/or licensed to LBI-DHP under one or more
 * contributor license agreements (LBI-DHP: Ludwig Boltzmann Institute
 * for Digital Health and Prevention -- A research institute of the
 * Ludwig Boltzmann Gesellschaft, Österreichische Vereinigung zur
 * Förderung der wissenschaftlichen Forschung).
 * Licensed under the Elastic License 2.0.
 */
package io.redlink.more.data.service;

import io.redlink.more.data.event.ParticipantUpdateEvent;
import io.redlink.more.data.model.ParticipantApplication;
import io.redlink.more.data.model.ParticipantConsent;
import io.redlink.more.data.model.RoutingInfo;
import io.redlink.more.data.repository.ParticipantApplicationRepository;
import io.redlink.more.data.repository.StudyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class ApplicationAccessService implements ApplicationListener<ParticipantUpdateEvent> {
    private static final Logger LOG = LoggerFactory.getLogger(ApplicationAccessService.class);
    private final LoginTokenService loginTokenService;
    private final ParticipantApplicationRepository participantApplicationRepository;
    private final StudyRepository studyRepository;

    public ApplicationAccessService(LoginTokenService loginTokenService, ParticipantApplicationRepository participantApplicationRepository, StudyRepository studyRepository) {
        this.loginTokenService = loginTokenService;
        this.participantApplicationRepository = participantApplicationRepository;
        this.studyRepository = studyRepository;
    }

    public Optional<RoutingInfo> validateLogin(Long studyId, String userDataReference, String loginToken) {
        Optional<ParticipantApplication> application = participantApplicationRepository.findByUserDataReference(studyId, userDataReference);
        if (application.isEmpty()) {
            LOG.warn("No application found for study {} and user data reference {}", studyId, userDataReference);
            return Optional.empty();
        }
        return loginTokenService.validateToken(loginToken, application.get());
    }

    public boolean hasConsent(RoutingInfo routingInfo) {
        Optional<ParticipantConsent> existingConsent = studyRepository.getConsent(routingInfo.studyId(), routingInfo.participantId());
        return existingConsent.map(ParticipantConsent::accepted).orElse(false);
    }

    public void validateAndStoreConsent(RoutingInfo routingInfo, ParticipantConsent consent) {
        if (consent.accepted()) {
            studyRepository.storeConsent(routingInfo.studyId(), routingInfo.participantId(), consent);
            studyRepository.updateParticipantStatus(routingInfo, "invited", "active");
        } else {
            LOG.warn("Consent {} is not accepted by user {}", consent, routingInfo);
            throw new IllegalStateException("Consent was not accepted!");
        }
    }

    @Override
    public void onApplicationEvent(ParticipantUpdateEvent event) {
        switch (event.getAction()) {
            case DELETE ->
                    participantApplicationRepository.deleteAllByParticipant(event.getStudyId(), event.getParticipantId());
        }
    }
}
