/*
 * Copyright (c) 2022 Redlink GmbH.
 */
package io.redlink.more.data.service;

import io.redlink.more.data.event.ParticipantUpdateAction;
import io.redlink.more.data.event.ParticipantUpdateEvent;
import io.redlink.more.data.exception.RegistrationNotPossibleException;
import io.redlink.more.data.model.ApiCredentials;
import io.redlink.more.data.model.ParticipantConsent;
import io.redlink.more.data.model.RoutingInfo;
import io.redlink.more.data.model.Study;
import io.redlink.more.data.repository.PushTokenRepository;
import io.redlink.more.data.repository.StudyRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class RegistrationService {

    private final StudyRepository studyRepository;
    private final PushTokenRepository pushTokenRepository;

    private final PasswordEncoder passwordEncoder;

    private final ApplicationEventPublisher eventPublisher;

    public RegistrationService(StudyRepository studyRepository, PushTokenRepository pushTokenRepository, PasswordEncoder passwordEncoder, ApplicationEventPublisher eventPublisher) {
        this.studyRepository = studyRepository;
        this.pushTokenRepository = pushTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.eventPublisher = eventPublisher;
    }

    public Optional<Study> loadStudyByRegistrationToken(String registrationToken) {
        return studyRepository.findByRegistrationToken(registrationToken);
    }

    public Optional<ApiCredentials> register(String registrationToken, ParticipantConsent consent) {
        if (!consent.accepted()) {
            throw RegistrationNotPossibleException.noConsentGiven();
        }
        var s = studyRepository.findByRegistrationToken(registrationToken);
        if (s.isEmpty()) {
            return Optional.empty();
        }
        final var study = s.get();
        if (!study.active()) {
            throw RegistrationNotPossibleException.studyNotActive(study);
        }

        var apiSecret = UUID.randomUUID().toString();

        try {
            return studyRepository.createCredentials(registrationToken, consent, () -> passwordEncoder.encode(apiSecret))
                    .map(apiId -> new ApiCredentials(apiId, apiSecret));
        } catch (DataIntegrityViolationException e) {
            throw new RegistrationNotPossibleException("ER-000", "Invalid Consent", e);
        }
    }

    public void unregister(String apiId, RoutingInfo routingInfo) {
        publishParticipantDeregistrationEvent(routingInfo, ParticipantUpdateAction.DELETE);
        pushTokenRepository.clearToken(routingInfo.studyId(), routingInfo.participantId());
        studyRepository.clearCredentials(apiId);
    }

    private void publishParticipantDeregistrationEvent(RoutingInfo routingInfo, ParticipantUpdateAction action) {
        eventPublisher.publishEvent(new ParticipantUpdateEvent(this, routingInfo.studyId(), routingInfo.participantId(), action));
    }
}
