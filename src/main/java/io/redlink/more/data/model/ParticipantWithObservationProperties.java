package io.redlink.more.data.model;

import java.util.Map;

public record ParticipantWithObservationProperties(
        Integer participantId,
        Long studyId,
        Integer observationId,
        Map<String, Object> properties
) {
    public ParticipantWithObservationProperties updateProperties(Map<String, Object> properties) {
        return new ParticipantWithObservationProperties(this.participantId, this.studyId, this.observationId, properties);
    }
}
