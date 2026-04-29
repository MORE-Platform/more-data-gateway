package io.redlink.more.data.model;

import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;

public class CallbackData {

    private RoutingInfo routingInfo;
    private Study study;
    private SimpleParticipant participant;
    private Observation observation;
    private Map<String, String> params;

    public CallbackData(
            Study study,
            Observation observation,
            SimpleParticipant participant,
            Map<String, String> params) {
        this.routingInfo = new RoutingInfo(study.studyId(), participant.id(), OptionalInt.empty(), Set.of(), true, true);
        this.study = study;
        this.participant = participant;
        this.observation = observation;
        this.params = params;
    }

    public RoutingInfo getRoutingInfo() {
        return routingInfo;
    }

    public Object studyId() {
        return study.studyId();
    }

    public Study getStudy() {
        return study;
    }

    public SimpleParticipant getParticipant() {
        return participant;
    }

    public int getObservationId() {
        return observation.observationId();
    }

    public Object observationId() {
        return observation.observationId();
    }

    public Observation getObservation() {
        return observation;
    }

    public Map<String, String> getParams() {
        return params;
    }

    @Override
    public String toString() {
        return "CallbackData{" +
                "studyId=" + routingInfo.studyId() +
                ", participantId=" + routingInfo.participantId() +
                ", observationId=" + observation.observationId() +
                ", params=" + params +
                '}';
    }

}
