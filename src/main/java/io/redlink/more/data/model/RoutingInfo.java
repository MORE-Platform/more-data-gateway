package io.redlink.more.data.model;

import java.util.OptionalInt;

public record RoutingInfo(long studyId, int participantId, OptionalInt studyGroupId) {
}
