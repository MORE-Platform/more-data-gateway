package io.redlink.more.data.model;

import java.util.Map;
import java.util.Set;

public record ParticipantKeyValue(
        Long studyId,
        Integer participantId,
        String key,
        Map<String, Object> value,
        Set<Integer> observationGroupIds
) {
}
