package io.redlink.more.data.model.scheduler;

public class RelativeRecurrenceRule {

    private Duration frequency;

    private Duration endAfter;

    public RelativeRecurrenceRule() {
    }

    public Duration getFrequency() {
        return frequency;
    }

    public RelativeRecurrenceRule setFrequency(Duration frequency) {
        this.frequency = frequency;
        return this;
    }

    public Duration getEndAfter() {
        return endAfter;
    }

    public RelativeRecurrenceRule setEndAfter(Duration endAfter) {
        this.endAfter = endAfter;
        return this;
    }
}
