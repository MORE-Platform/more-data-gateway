package io.redlink.more.data.model;

import java.io.Serializable;
import java.util.OptionalInt;

public record ApiRoutingInfo(
        long studyId,
        int observationId,
        String observationType,
        int participantId,
        int rawStudyGroupId,
        boolean studyActive
) implements Serializable {

    public ApiRoutingInfo(long studyId,
                       int observationId,
                       String observationType,
                       int participantId,
                       @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
                       OptionalInt studyGroupId,
                       boolean studyActive
    ) {
        this(studyId, observationId, observationType, participantId, studyGroupId.orElse(Integer.MIN_VALUE), studyActive);
    }

    public OptionalInt studyGroupId() {
        if (this.rawStudyGroupId < 0) {
            return OptionalInt.empty();
        } else {
            return OptionalInt.of(rawStudyGroupId);
        }
    }
}
