package io.redlink.more.data.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(code = HttpStatus.NOT_FOUND)
public class NotFoundException extends RuntimeException {

    public NotFoundException(String cause) { super(cause); }

    public static NotFoundException Participant(Integer id) {
        return new NotFoundException(String.format("Participant with id %s cannot be found", id.toString()));
    }
}
