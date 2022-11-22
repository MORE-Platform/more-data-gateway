/*
 * Copyright (c) 2022 Redlink GmbH.
 */
package io.redlink.more.data.controller;

import io.redlink.more.data.api.app.v1.model.ObservationDTO;
import io.redlink.more.data.api.app.v1.model.StudyDTO;
import io.redlink.more.data.model.Observation;
import io.redlink.more.data.model.Study;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class StudyTransformer {
    public StudyDTO toDTO(Study study) {
        return new StudyDTO()
                .studyTitle(study.title())
                .participantInfo(study.participantInfo())
                .consentInfo(study.consentInfo())
                .start(study.startDate())
                .end(study.endDate())
                .observations(toDTO(study.observations()))
                ;
    }

    public List<ObservationDTO> toDTO(List<Observation> observations) {
        return observations.stream().map(this::toDTO).toList();
    }

    public ObservationDTO toDTO(Observation observation) {
        return new ObservationDTO()
                .observationId(String.valueOf(observation.observationId()))
                .observationType(observation.type())
                .observationTitle(observation.title())
                .participantInfo(observation.participantInfo())
                ;
    }

}
