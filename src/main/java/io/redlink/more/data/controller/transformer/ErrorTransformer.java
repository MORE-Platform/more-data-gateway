/*
 * Copyright (c) 2023 Redlink GmbH.
 */
package io.redlink.more.data.controller.transformer;

import io.redlink.more.data.api.app.v1.model.ErrorDTO;
import io.redlink.more.data.exception.RegistrationNotPossibleException;

public final class ErrorTransformer {

    private ErrorTransformer() {}

    public static ErrorDTO toDTO(RegistrationNotPossibleException exception) {
        return new ErrorDTO()
                .code(exception.getErrorCode())
                .msg(exception.getMessage());
    }

    public static ErrorDTO toDTO(RuntimeException ex) {
        return new ErrorDTO()
                .code("EX-000")
                .msg(ex.getMessage());
    }
}
