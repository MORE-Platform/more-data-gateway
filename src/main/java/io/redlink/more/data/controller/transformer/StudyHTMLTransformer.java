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
