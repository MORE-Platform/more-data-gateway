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
