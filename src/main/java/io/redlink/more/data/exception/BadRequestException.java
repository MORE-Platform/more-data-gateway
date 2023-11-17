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

@ResponseStatus(code = HttpStatus.BAD_REQUEST)
public class BadRequestException extends RuntimeException {
    public BadRequestException(String cause) { super(cause); }

    public static BadRequestException StudyGroup(Integer observationStudyGroup, Integer participantStudyGroup) {
        return new BadRequestException(
                String.format(
                        "Observation requires studyGroup %s, but given participant has studyGroup %s",
                        observationStudyGroup.toString(),
                        participantStudyGroup.toString()
                )
        );
    }

    public static BadRequestException TimeFrame(){
        return new BadRequestException("DataBulk Invalid: At least one dataPoint was recorded outside of required timeframe");
    }
}
