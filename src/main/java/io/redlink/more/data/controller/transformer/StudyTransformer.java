/*
 * Copyright (c) 2022 Redlink GmbH.
 */
package io.redlink.more.data.controller.transformer;

import io.redlink.more.data.api.app.v1.model.ObservationDTO;
import io.redlink.more.data.api.app.v1.model.StudyDTO;
import io.redlink.more.data.model.Observation;
import io.redlink.more.data.model.Study;
import java.util.List;

public final class StudyTransformer {

    private StudyTransformer() {}

    public static StudyDTO toDTO(Study study) {
        return new StudyDTO()
                .studyTitle(study.title())
                .participantInfo(study.participantInfo())
                .consentInfo(study.consentInfo())
                .start(study.startDate())
                .end(study.endDate())
                .observations(toDTO(study.observations()))
                ;
    }

    public static List<ObservationDTO> toDTO(List<Observation> observations) {
        return observations.stream().map(StudyTransformer::toDTO).toList();
    }

    public static ObservationDTO toDTO(Observation observation) {
        return new ObservationDTO()
                .observationId(String.valueOf(observation.observationId()))
                .observationType(observation.type())
                .observationTitle(observation.title())
                .participantInfo(observation.participantInfo())
                ;
    }

}
