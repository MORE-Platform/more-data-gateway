/*
 * Copyright LBI-DHP and/or licensed to LBI-DHP under one or more
 * contributor license agreements (LBI-DHP: Ludwig Boltzmann Institute
 * for Digital Health and Prevention -- A research institute of the
 * Ludwig Boltzmann Gesellschaft, Oesterreichische Vereinigung zur
 * Foerderung der wissenschaftlichen Forschung).
 * Licensed under the Elastic License 2.0.
 */
package io.redlink.more.data.model;

import java.io.Serializable;
import java.util.OptionalInt;

public record RoutingInfo(
        long studyId,
        int participantId,
        int rawStudyGroupId,
        boolean studyActive
) implements Serializable {

    public RoutingInfo(long studyId,
                       int participantId,
                       @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
                       OptionalInt studyGroupId,
                       boolean studyActive
    ) {
        this(studyId, participantId, studyGroupId.orElse(Integer.MIN_VALUE), studyActive);
    }

    public RoutingInfo(ApiRoutingInfo routingInfo, Integer participantId) {
        this(routingInfo.studyId(), participantId, routingInfo.studyGroupId(), routingInfo.studyActive());
    }

    public OptionalInt studyGroupId() {
        if (this.rawStudyGroupId < 0) {
            return OptionalInt.empty();
        } else {
            return OptionalInt.of(rawStudyGroupId);
        }
    }
}
