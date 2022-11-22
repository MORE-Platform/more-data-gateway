/*
 * Copyright (c) 2022 Redlink GmbH.
 */
package io.redlink.more.data.service;

import io.redlink.more.data.model.ApiCredentials;
import io.redlink.more.data.model.ParticipantConsent;
import io.redlink.more.data.model.RoutingInfo;
import io.redlink.more.data.model.Study;
import io.redlink.more.data.repository.StudyRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class RegistrationService {

    private final StudyRepository studyRepository;

    private final PasswordEncoder passwordEncoder;

    public RegistrationService(StudyRepository studyRepository, PasswordEncoder passwordEncoder) {
        this.studyRepository = studyRepository;
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
            throw new IllegalArgumentException("Consent not accepted");
        }

        var apiSecret = UUID.randomUUID().toString();

        return studyRepository.createCredentials(registrationToken, consent, () -> passwordEncoder.encode(apiSecret))
                .map(apiId -> new ApiCredentials(apiId, apiSecret));
    }

}
