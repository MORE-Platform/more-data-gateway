/*
 * Copyright (c) 2023 Redlink GmbH.
 */
package io.redlink.more.data.controller.transformer;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public final class BaseTransformers {

    private BaseTransformers() {}

    public static Long toVersionTag(Instant modified) {
        if (modified == null) return null;
        return modified.toEpochMilli();
    }

    public static Instant toInstant(OffsetDateTime dateTime) {
        if (dateTime == null)
            return null;
        return dateTime.toInstant();
    }

    public static OffsetDateTime toOffsetDateTime(Instant instant) {
        if (instant == null) return null;
        return instant.atOffset(ZoneOffset.UTC);
    }
}
