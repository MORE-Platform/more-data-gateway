package io.redlink.more.data.event;

import org.springframework.context.ApplicationEvent;

public class ParticipantUpdateEvent extends ApplicationEvent {
    private final Long studyId;
    private final Integer participantId;
    private final ParticipantUpdateAction action;

    public ParticipantUpdateEvent(Object source, Long studyId, Integer participantId, ParticipantUpdateAction action) {
        super(source);
        this.studyId = studyId;
        this.participantId = participantId;
        this.action = action;
    }

    public Long getStudyId() {
        return studyId;
    }

    public Integer getParticipantId() {
        return participantId;
    }

    public ParticipantUpdateAction getAction() {
        return action;
    }
}
