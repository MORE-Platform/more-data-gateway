/*
 * Copyright (c) 2023 Redlink GmbH.
 */
package io.redlink.more.data.controller.transformer;

import java.time.Instant;

public final class BaseTransformers {

    private BaseTransformers() {}

    public static Long toVersionTag(Instant modified) {
        if (modified == null) return null;
        return modified.toEpochMilli();
    }

}
