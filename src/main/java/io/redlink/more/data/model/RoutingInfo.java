package io.redlink.more.data.model;

import java.io.Serializable;
import java.util.OptionalInt;

public record RoutingInfo(long studyId, int participantId, OptionalInt studyGroupId) implements Serializable {
}
