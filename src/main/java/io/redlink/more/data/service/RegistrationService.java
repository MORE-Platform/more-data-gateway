/*
 * Copyright (c) 2022 Redlink GmbH.
 */
package io.redlink.more.data.service;

import io.redlink.more.data.model.ApiCredentials;
import io.redlink.more.data.model.ParticipantConsent;
import io.redlink.more.data.model.Study;
import io.redlink.more.data.repository.StudyRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class RegistrationService {

    private final StudyRepository studyRepository;

    public RegistrationService(StudyRepository studyRepository) {
        this.studyRepository = studyRepository;
    }

    public Optional<Study> loadStudyByRegistrationToken(String registrationToken) {
        return studyRepository.findByRegistrationToken(registrationToken);
    }


    public boolean validateConsent(ParticipantConsent consent) {
        return consent.accepted();
    }

    public Optional<ApiCredentials> register(String registrationToken, ParticipantConsent consent) {
        if (!validateConsent(consent)) {
            throw new IllegalArgumentException("Consent not accepted");
        }

        return studyRepository.createCredentials(registrationToken, consent,
                // FIXME: this is insecure!
                () -> "password");
    }



}
