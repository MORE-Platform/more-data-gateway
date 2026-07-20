/*
 * Copyright LBI-DHP and/or licensed to LBI-DHP under one or more
 * contributor license agreements (LBI-DHP: Ludwig Boltzmann Institute
 * for Digital Health and Prevention -- A research institute of the
 * Ludwig Boltzmann Gesellschaft, Österreichische Vereinigung zur
 * Förderung der wissenschaftlichen Forschung).
 * Licensed under the Elastic License 2.0.
 */
package io.redlink.more.data.model;

import java.util.Objects;
import java.util.UUID;

public class ParticipantApplication {

    private Long studyId;
    private Integer participantId;
    private String application;
    private UUID uuid;

    public ParticipantApplication() {
    }

    public Long getStudyId() {
        return studyId;
    }

    public ParticipantApplication setStudyId(Long studyId) {
        this.studyId = studyId;
        return this;
    }

    public Integer getParticipantId() {
        return participantId;
    }

    public ParticipantApplication setParticipantId(Integer participantId) {
        this.participantId = participantId;
        return this;
    }

    public String getApplication() {
        return application;
    }

    public ParticipantApplication setApplication(String application) {
        this.application = application;
        return this;
    }

    public UUID getUuid() {
        return uuid;
    }

    public ParticipantApplication setUuid(UUID uuid) {
        this.uuid = uuid;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ParticipantApplication that = (ParticipantApplication) o;
        return Objects.equals(studyId, that.studyId) && Objects.equals(participantId, that.participantId) && Objects.equals(application, that.application);
    }

    @Override
    public int hashCode() {
        return Objects.hash(studyId, participantId, application);
    }

    @Override
    public String toString() {
        return "ParticipantApplication{" +
                "studyId=" + studyId +
                ", participantId=" + participantId +
                ", application='" + application + '\'' +
                ", uuid=" + uuid +
                '}';
    }
}
