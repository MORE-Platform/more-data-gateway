package io.redlink.more.data.model.garmin;

import java.util.HashMap;
import java.util.Map;

public record GarminAuthenticationValues(
        String state,
        String challengeCode,
        String requestUrl
) {
    public Map<String, String> toMap() {
        Map<String, String> map = new HashMap<>();
        map.put("state", state);
        map.put("challengeCode", challengeCode);
        map.put("requestUrl", requestUrl);
        return map;
    }

    public static GarminAuthenticationValues fromMap(Map<String, String> map) {
        return new GarminAuthenticationValues(
                map.get("state"),
                map.get("challengeCode"),
                map.get("requestUrl")
        );
    }
}
