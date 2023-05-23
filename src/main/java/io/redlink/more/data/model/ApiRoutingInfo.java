package io.redlink.more.data.model;

import java.io.Serializable;
import java.util.OptionalInt;

public record ApiRoutingInfo(
        String observationType,
        int rawStudyGroupId,
        boolean studyActive,
        String secret
) implements Serializable {

    public ApiRoutingInfo(String observationType,
                       @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
                       OptionalInt studyGroupId,
                       boolean studyActive,
                          String secret
    ) {
        this(observationType, studyGroupId.orElse(Integer.MIN_VALUE), studyActive, secret);
    }

    public OptionalInt studyGroupId() {
        if (this.rawStudyGroupId < 0) {
            return OptionalInt.empty();
        } else {
            return OptionalInt.of(rawStudyGroupId);
        }
    }
}
