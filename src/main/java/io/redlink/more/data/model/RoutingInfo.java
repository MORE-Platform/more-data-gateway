package io.redlink.more.data.model;

import java.io.Serializable;
import java.util.OptionalInt;

public record RoutingInfo(long studyId, int participantId, int rawStudyGroupId) implements Serializable {

    public RoutingInfo(long studyId, int participantId,
                       @SuppressWarnings("OptionalUsedAsFieldOrParameterType") OptionalInt studyGroupId) {
        this(studyId, participantId, studyGroupId.orElse(Integer.MIN_VALUE));
    }

    public OptionalInt studyGroupId() {
        if (this.rawStudyGroupId < 0) {
            return OptionalInt.empty();
        } else {
            return OptionalInt.of(rawStudyGroupId);
        }
    }

}
