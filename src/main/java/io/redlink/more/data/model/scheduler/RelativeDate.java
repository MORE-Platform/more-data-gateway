package io.redlink.more.data.model.scheduler;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalTimeSerializer;
import java.time.LocalTime;
import java.time.ZoneId;

public class RelativeDate {

    private Duration offset;
    @JsonSerialize(using = LocalTimeSerializer.class)
    @JsonDeserialize(using = LocalTimeDeserializer.class)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private LocalTime time;
    private String timezone;

    public RelativeDate() {
    }

    public ZoneId getZoneId() {
        return timezone != null ? ZoneId.of(timezone) : ZoneId.of("Europe/Berlin");
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

    public String getTimezone() {
        return timezone;
    }

    public RelativeDate setTimezone(String timezone) {
        this.timezone = timezone;
        return this;
    }
}
