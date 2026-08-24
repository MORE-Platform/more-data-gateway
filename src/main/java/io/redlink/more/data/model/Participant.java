/*
 * Copyright LBI-DHP and/or licensed to LBI-DHP under one or more
 * contributor license agreements (LBI-DHP: Ludwig Boltzmann Institute
 * for Digital Health and Prevention -- A research institute of the
 * Ludwig Boltzmann Gesellschaft, Oesterreichische Vereinigung zur
 * Foerderung der wissenschaftlichen Forschung).
 * Licensed under the Apache 2.0 license (see https://www.apache.org/licenses/LICENSE-2.0).
 */
package io.redlink.more.data.model;

import java.time.Instant;
import java.util.Collection;
import java.util.OptionalInt;

public record Participant(
        int id,
        String alias,
        String status,
        OptionalInt studyGroupId,
        String studyGroupTitle,
        Instant start,
        Collection<ObservationGroupInfo> observationGroups
) {
    public record ObservationGroupInfo(
            Integer id,
            String title
    ){}
}
