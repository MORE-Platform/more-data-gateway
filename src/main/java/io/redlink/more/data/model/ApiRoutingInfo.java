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

public record ApiRoutingInfo(
        Long studyId,
        Integer observationId,
        String observationType,
        int rawStudyGroupId,
        boolean studyActive,
        String secret
) implements Serializable {

    public ApiRoutingInfo(Long studyId,
                          Integer observationId,
                          String observationType,
                          @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
                          OptionalInt studyGroupId,
                          boolean studyActive,
                          String secret
    ) {
        this(studyId, observationId, observationType, studyGroupId.orElse(Integer.MIN_VALUE), studyActive, secret);
    }

    public OptionalInt studyGroupId() {
        if (this.rawStudyGroupId < 0) {
            return OptionalInt.empty();
        } else {
            return OptionalInt.of(rawStudyGroupId);
        }
    }

    public ApiRoutingInfo withParticipantStudyGroup(
            @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
            OptionalInt studyGroupId)
    {
        return new ApiRoutingInfo(
                studyId,
                observationId,
                observationType,
                studyGroupId.orElse(Integer.MIN_VALUE),
                studyActive,
                secret
        );
    }
}
