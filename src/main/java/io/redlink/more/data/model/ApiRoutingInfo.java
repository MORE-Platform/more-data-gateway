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
import java.util.HashSet;
import java.util.OptionalInt;
import java.util.Set;

/**
 * API routing Info where the study and the observation are given and the participant is provided by the data
 * @param studyId the study
 * @param observationId the observation
 * @param observationType the type of the observation
 * @param rawStudyGroupId the study group assigned to the observation (if any)
 * @param observationGroupIds the observation group ids assigned to the observation (empty if none)
 * @param studyActive if the study is active
 * @param secret the secret
 */
public record ApiRoutingInfo(
        Long studyId,
        Integer observationId,
        String observationType,
        int rawStudyGroupId,
        Set<Integer> observationGroupIds,
        boolean studyActive,
        String secret
) implements Serializable {

    public ApiRoutingInfo(Long studyId,
                          Integer observationId,
                          String observationType,
                          @SuppressWarnings("OptionalUsedAsFieldOrParameterType") OptionalInt studyGroupId,
                          Set<Integer> observationGroupIds,
                          boolean studyActive,
                          String secret
    ) {
        this(studyId, observationId, observationType, studyGroupId.orElse(Integer.MIN_VALUE), observationGroupIds == null ? new HashSet<>() : observationGroupIds, studyActive, secret);
    }

    public OptionalInt studyGroupId() {
        if (this.rawStudyGroupId < 0) {
            return OptionalInt.empty();
        } else {
            return OptionalInt.of(rawStudyGroupId);
        }
    }

}
