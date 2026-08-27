/*
 * Copyright LBI-DHP and/or licensed to LBI-DHP under one or more
 * contributor license agreements (LBI-DHP: Ludwig Boltzmann Institute
 * for Digital Health and Prevention -- A research institute of the
 * Ludwig Boltzmann Gesellschaft, Oesterreichische Vereinigung zur
 * Foerderung der wissenschaftlichen Forschung).
 * Licensed under the Apache 2.0 license (see https://www.apache.org/licenses/LICENSE-2.0).
 */
package io.redlink.more.data.model.scheduler;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalTimeSerializer;
import java.time.LocalTime;

public class RelativeDate {

    private Duration offset;
    @JsonSerialize(using = LocalTimeSerializer.class)
    @JsonDeserialize(using = LocalTimeDeserializer.class)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private LocalTime time;

    public RelativeDate() {
    }

    public int getHours() {
        return time.getHour();
    }

    public int getMinutes() {
        return time.getMinute();
    }

    public Duration getOffset() {
        return offset;
    }

    public RelativeDate setOffset(Duration offset) {
        this.offset = offset;
        return this;
    }

    public LocalTime getTime() {
        return time;
    }

    public RelativeDate setTime(LocalTime time) {
        this.time = time;
        return this;
    }

}
