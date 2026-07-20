package io.redlink.more.data.controller;

import io.redlink.more.data.api.app.v1.model.ErrorDTO;
import io.redlink.more.data.controller.transformer.ErrorTransformer;
import io.redlink.more.data.exception.BadRequestException;
import io.redlink.more.data.exception.ForbiddenException;
import io.redlink.more.data.exception.NotAuthorizedException;
import io.redlink.more.data.exception.NotFoundException;
import io.redlink.more.data.exception.RegistrationNotPossibleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GlobalControllerExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalControllerExceptionHandler.class);

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<Void> handleForbidden(ForbiddenException e) {
        LOG.warn("Forbidden: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Void> handleNotFound(NotFoundException e) {
        LOG.warn("Not Found: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    @ExceptionHandler(NotAuthorizedException.class)
    public ResponseEntity<Void> handleNotAuthorized(NotAuthorizedException e) {
        LOG.warn("Not Authorized: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<Void> handleBadRequest(BadRequestException e) {
        LOG.warn("Bad Request: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Void> handleAccessDenied(AccessDeniedException e) {
        LOG.warn("Access Denied: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    @ExceptionHandler(RegistrationNotPossibleException.class)
    public ResponseEntity<ErrorDTO> handleRegistrationError(RegistrationNotPossibleException rnpe) {
        LOG.warn("Registration not possible: [{}] {}", rnpe.getErrorCode(), rnpe.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorTransformer.toDTO(rnpe));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorDTO> handleError(RuntimeException ex) {
        LOG.error("Unexpected runtime error: {}", ex.getMessage(), ex);
        return ResponseEntity.internalServerError()
                .body(ErrorTransformer.toDTO(ex));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Void> handleIllegalState(IllegalStateException ex) {
        LOG.error("Illegal state: {}", ex.getMessage());
        return ResponseEntity.internalServerError().build();
    }
}
