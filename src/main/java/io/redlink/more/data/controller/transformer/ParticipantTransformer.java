/*
 * Copyright LBI-DHP and/or licensed to LBI-DHP under one or more
 * contributor license agreements (LBI-DHP: Ludwig Boltzmann Institute
 * for Digital Health and Prevention -- A research institute of the
 * Ludwig Boltzmann Gesellschaft, Oesterreichische Vereinigung zur
 * Foerderung der wissenschaftlichen Forschung).
 * Licensed under the Apache 2.0 license (see https://www.apache.org/licenses/LICENSE-2.0).
 */
package io.redlink.more.data.controller.transformer;

import io.redlink.more.data.api.app.v1.model.ParticipantDTO;
import io.redlink.more.data.api.app.v1.model.ParticipantStatusDTO;
import io.redlink.more.data.api.app.v1.model.ParticipantStudyGroupDTO;
import io.redlink.more.data.model.Participant;

public final class ParticipantTransformer {

    private ParticipantTransformer() {}

    public static ParticipantDTO toDTO(Participant participant) {
        if (participant == null) {
            return null;
        }
        return new ParticipantDTO(
                String.valueOf(participant.id()),
                participant.alias(),
                ParticipantStatusDTO.fromValue(participant.status()),
                toGroupDto(participant),
                participant.start()
        );
    }

    private static ParticipantStudyGroupDTO toGroupDto(Participant participant) {
        if (participant.studyGroupId().isPresent()) {
            final int groupId = participant.studyGroupId().getAsInt();
            return new ParticipantStudyGroupDTO(
                    String.valueOf(groupId),
                    participant.studyGroupTitle()
            );
        } else {
            return null;
        }
    }
}
