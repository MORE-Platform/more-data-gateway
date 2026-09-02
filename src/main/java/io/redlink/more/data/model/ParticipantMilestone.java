package io.redlink.more.data.model;

import java.time.Instant;

public record ParticipantMilestone(
        Long studyId,
        Integer participantId,
        Integer milestoneId,
        Integer participantMilestoneId,
        Instant dateTime,
        Instant created,
        Instant modified
) {
}
