/*
 * Copyright (c) 2022 Redlink GmbH.
 */
package io.redlink.more.data.service;

import io.redlink.more.data.controller.exception.RegistrationNotPossibleException;
import io.redlink.more.data.model.ApiCredentials;
import io.redlink.more.data.model.ParticipantConsent;
import io.redlink.more.data.model.RoutingInfo;
import io.redlink.more.data.model.Study;
import io.redlink.more.data.repository.PushTokenRepository;
import io.redlink.more.data.repository.StudyRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class RegistrationService {

    private final StudyRepository studyRepository;
    private final PushTokenRepository pushTokenRepository;

    private final PasswordEncoder passwordEncoder;

    public RegistrationService(StudyRepository studyRepository, PushTokenRepository pushTokenRepository, PasswordEncoder passwordEncoder) {
        this.studyRepository = studyRepository;
        this.pushTokenRepository = pushTokenRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public Optional<Study> loadStudyByRegistrationToken(String registrationToken) {
        return studyRepository.findByRegistrationToken(registrationToken);
    }

    public Optional<Study> loadStudyByRoutingInfo(RoutingInfo routingInfo) {
        return Optional.ofNullable(routingInfo)
                .flatMap(studyRepository::findStudy);
    }

    public boolean validateConsent(ParticipantConsent consent) {
        return consent.accepted();
    }

    public Optional<ApiCredentials> register(String registrationToken, ParticipantConsent consent) {
        if (!validateConsent(consent)) {
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
            throw new IllegalArgumentException("Invalid Consent", e);
        }
    }

    public void unregister(String apiId, RoutingInfo routingInfo) {
        pushTokenRepository.clearToken(routingInfo.studyId(), routingInfo.participantId());
        studyRepository.clearCredentials(apiId);
    }
}
