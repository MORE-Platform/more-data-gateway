/*
 * Copyright LBI-DHP and/or licensed to LBI-DHP under one or more
 * contributor license agreements (LBI-DHP: Ludwig Boltzmann Institute
 * for Digital Health and Prevention -- A research institute of the
 * Ludwig Boltzmann Gesellschaft, Oesterreichische Vereinigung zur
 * Foerderung der wissenschaftlichen Forschung).
 * Licensed under the Elastic License 2.0.
 */
package io.redlink.more.data.schedule;

import io.redlink.more.data.model.scheduler.Event;
import io.redlink.more.data.model.scheduler.ScheduleEvent;
import io.redlink.more.data.model.scheduler.StudyWideEvent;
import io.redlink.more.data.util.MapperUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScheduleEventSerializationTest {

    @Test
    @DisplayName("The schedule the study-manager writes for a study-wide observation is read back as such")
    void studyWideEventIsReadFromTheDatabaseFormat() {
        // exactly what io.redlink.more.studymanager.model.scheduler.StudyWideEvent serializes to
        assertThat(MapperUtils.readValue("{\"type\":\"StudyWideEvent\",\"randomization\":null}", ScheduleEvent.class))
                .isInstanceOf(StudyWideEvent.class);
    }

    @Test
    @DisplayName("An unknown schedule type falls back to the default instead of failing")
    void unknownTypeFallsBackToDefaultImpl() {
        // a schedule type introduced by a newer study-manager must not break reading
        // observations here, so there is no deployment ordering constraint between the two
        assertThat(MapperUtils.readValue("{\"type\":\"SomeFutureEvent\"}", ScheduleEvent.class))
                .isInstanceOf(Event.class);
    }
}
