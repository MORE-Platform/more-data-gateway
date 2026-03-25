package io.redlink.more.data.model;


public record ResolvedInterventionToken(
    Long studyId,
    Integer interventionId,
    boolean studyActive
) {} 