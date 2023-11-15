package io.redlink.more.data.model.scheduler;

public class RelativeDate {

    private Duration offset;
    private String time;

    public RelativeDate() {
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
}
