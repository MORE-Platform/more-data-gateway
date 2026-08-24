/*
 * Copyright LBI-DHP and/or licensed to LBI-DHP under one or more
 * contributor license agreements (LBI-DHP: Ludwig Boltzmann Institute
 * for Digital Health and Prevention -- A research institute of the
 * Ludwig Boltzmann Gesellschaft, Oesterreichische Vereinigung zur
 * Foerderung der wissenschaftlichen Forschung).
 * Licensed under the Apache 2.0 license (see https://www.apache.org/licenses/LICENSE-2.0).
 */
/*
 * Copyright (c) 2025 Redlink GmbH.
 */
package io.redlink.more.data.controller.transformer;

import io.redlink.more.data.model.Study;

public final class StudyHTMLTransformer  {
    private StudyHTMLTransformer() {}

    public static String toString(Study study) {
        return study.title();
    }
}
