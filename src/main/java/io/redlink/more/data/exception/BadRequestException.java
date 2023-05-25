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
}
