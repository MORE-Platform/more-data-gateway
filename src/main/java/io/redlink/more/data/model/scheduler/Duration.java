package io.redlink.more.data.model.scheduler;

import com.fasterxml.jackson.annotation.JsonCreator;

public class Duration {

    private Integer value;

    /**
     * unit of time to offset
     */
    public enum Unit {
        MINUTE("MINUTE"),

        HOUR("HOUR"),

        DAY("DAY");

        private String value;

        Unit(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        @Override
        public String toString() {
            return String.valueOf(value);
        }

        @JsonCreator
        public static Unit fromValue(String value) {
            for (Unit b : Unit.values()) {
                if (b.value.equals(value)) {
                    return b;
                }
            }
            throw new IllegalArgumentException("Unexpected value '" + value + "'");
        }
    }

    private Unit unit;

    public Duration() {
    }

    public Integer getValue() {
        return value;
    }

    public Duration setValue(Integer value) {
        this.value = value;
        return this;
    }

    public Unit getUnit() {
        return unit;
    }

    public Duration setUnit(Unit unit) {
        this.unit = unit;
        return this;
    }

    @Override
    public String toString() {
        return "Duration{" +
                "offset=" + value +
                ", unit=" + unit +
                '}';
    }
}
