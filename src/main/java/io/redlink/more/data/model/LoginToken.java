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

public class LoginToken {

    private Long studyId;
    private Integer participantId;
    private String application;
    private String code;
    private String codeHash;

    public LoginToken() {
    }

    public Long getStudyId() {
        return studyId;
    }

    public LoginToken setStudyId(Long studyId) {
        this.studyId = studyId;
        return this;
    }

    public Integer getParticipantId() {
        return participantId;
    }

    public LoginToken setParticipantId(Integer participantId) {
        this.participantId = participantId;
        return this;
    }

    public String getApplication() {
        return application;
    }

    public LoginToken setApplication(String application) {
        this.application = application;
        return this;
    }

    public String getCode() {
        return code;
    }

    public LoginToken setCode(String code) {
        this.code = code;
        return this;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public LoginToken setCodeHash(String codeHash) {
        this.codeHash = codeHash;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LoginToken that = (LoginToken) o;
        return Objects.equals(studyId, that.studyId) && Objects.equals(participantId, that.participantId) && Objects.equals(application, that.application);
    }

    @Override
    public int hashCode() {
        return Objects.hash(studyId, participantId, application);
    }

    @Override
    public String toString() {
        return "LoginToken{" +
                "studyId=" + studyId +
                ", participantId=" + participantId +
                ", application='" + application + '\'' +
                '}';
    }
}
