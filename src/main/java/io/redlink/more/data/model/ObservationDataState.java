package io.redlink.more.data.model;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum ObservationDataState {
    MISSING("missing"),
    INCOMPLETE("incomplete"),
    PARTIAL("partial"),
    COMPLETE("complete");
    private final String value;

    ObservationDataState(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return "ObservationDataState{" +
                "name='" + name() + "', " +
                "value='" + value + '\'' +
                '}';
    }

    private static final Map<String, ObservationDataState> LOOKUP;

    static {
        LOOKUP = Arrays.stream(ObservationDataState.values()).collect(Collectors.toMap(ObservationDataState::getValue, Function.identity()));
    }

    public static ObservationDataState fromValue(String value) {
        return LOOKUP.get(value);
    }
}
