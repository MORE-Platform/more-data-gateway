/*
 * Copyright LBI-DHP and/or licensed to LBI-DHP under one or more
 * contributor license agreements (LBI-DHP: Ludwig Boltzmann Institute
 * for Digital Health and Prevention -- A research institute of the
 * Ludwig Boltzmann Gesellschaft, Oesterreichische Vereinigung zur
 * Foerderung der wissenschaftlichen Forschung).
 * Licensed under the Apache 2.0 license (see https://www.apache.org/licenses/LICENSE-2.0).
 */
/*
 * Copyright (c) 2023 Redlink GmbH.
 */
package io.redlink.more.data.exception;

import io.redlink.more.data.model.Observation;
import io.redlink.more.data.model.Study;

public class RegistrationNotPossibleException extends RuntimeException {

    private final String errorCode;

    public RegistrationNotPossibleException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public RegistrationNotPossibleException(String errorCode, String message, Throwable t) {
        super(message, t);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }


    public static RegistrationNotPossibleException studyNotActive(Study study) {
        return new RegistrationNotPossibleException(
                "ER-001",
                "Study '%s' is currently not active".formatted(study.title())
        );
    }

    public static RegistrationNotPossibleException noConsentGiven() {
        return new RegistrationNotPossibleException("ER-002", "Consent not given");
    }

    public static RegistrationNotPossibleException requiredObservationMissing(Observation observation) {
        return new RegistrationNotPossibleException("ER-003",
                "Required observation %s not enabled".formatted(observation.title())
        );
    }
}
