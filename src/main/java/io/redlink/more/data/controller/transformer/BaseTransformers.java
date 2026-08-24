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
package io.redlink.more.data.controller.transformer;

import java.time.Instant;

public final class BaseTransformers {

    private BaseTransformers() {}

    public static Long toVersionTag(Instant modified) {
        if (modified == null) return null;
        return modified.toEpochMilli();
    }

}
