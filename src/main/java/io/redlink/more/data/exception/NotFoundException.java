/*
 * Copyright LBI-DHP and/or licensed to LBI-DHP under one or more
 * contributor license agreements (LBI-DHP: Ludwig Boltzmann Institute
 * for Digital Health and Prevention -- A research institute of the
 * Ludwig Boltzmann Gesellschaft, Oesterreichische Vereinigung zur
 * Foerderung der wissenschaftlichen Forschung).
 * Licensed under the Elastic License 2.0.
 */
package io.redlink.more.data.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(code = HttpStatus.NOT_FOUND)
public class NotFoundException extends RuntimeException {

    public NotFoundException(String cause) { super(cause); }

    public static NotFoundException Participant(Integer id) {
        return new NotFoundException(String.format("Participant with id %s cannot be found", id.toString()));
    }

    public static NotFoundException Observation(Integer id) {
        return new NotFoundException(String.format("Observation with id %s cannot be found", id.toString()));
    }
}
