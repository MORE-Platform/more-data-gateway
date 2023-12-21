package io.redlink.more.data.model.scheduler;

import java.time.ZoneId;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RelativeDate {

    private static Pattern CLOCK = Pattern.compile("(\\d?\\d):(\\d\\d)");

    private Duration offset;
    private String time;
    private String timezone;

    public RelativeDate() {
    }

    public ZoneId getZoneId() {
        return timezone != null ? ZoneId.of(timezone) : ZoneId.of("Europe/Berlin");
    }

    public int getHours() {
        return getTimeGroup(1);
    }

    public int getMinutes() {
        return getTimeGroup(2);
    }

    private int getTimeGroup(int i) {
        if(time == null) {
            return 0;
        }
        Matcher m = CLOCK.matcher(time);
        if(m.find()) {
            return Integer.parseInt(m.group(i));
        } else {
            return 0;
        }
    }

    public Duration getOffset() {
        return offset;
    }

    public RelativeDate setOffset(Duration offset) {
        this.offset = offset;
        return this;
    }

    public String getTime() {
        return time;
    }

    public RelativeDate setTime(String time) {
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
