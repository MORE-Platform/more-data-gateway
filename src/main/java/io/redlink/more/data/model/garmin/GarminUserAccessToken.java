package io.redlink.more.data.model.garmin;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public record GarminUserAccessToken(

        @JsonProperty("access_token")
        String accessToken,
        @JsonProperty("refresh_token")
        String refreshToken,
        @JsonProperty("token_type")
        String tokenType,
        @JsonProperty("expires_in")
        Integer expiresIn,
        String scope,
        @JsonProperty("refresh_token_expires_in")
        Integer refreshTokenExpiresIn
) {

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("accessToken", encodeToBase64(accessToken));
        map.put("refreshToken", encodeToBase64(refreshToken));
        map.put("tokenType", tokenType);
        map.put("expiresIn", expiresIn);
        map.put("scope", scope);
        map.put("refreshTokenExpiresIn", refreshTokenExpiresIn);
        return map;
    }

    public static GarminUserAccessToken fromMap(Map<String, Object> map) {
        return new GarminUserAccessToken(
                decodeFromBase64((String) map.get("accessToken")),
                decodeFromBase64((String) map.get("refreshToken")),
                (String) map.get("tokenType"),
                (Integer) map.get("expiresIn"),
                (String) map.get("scope"),
                (Integer) map.get("refreshTokenExpiresIn")
        );
    }

    private static String encodeToBase64(String value) {
        if (value == null) {
            return null;
        }
        return Base64.getEncoder().encodeToString(value.getBytes());
    }

    private static String decodeFromBase64(String encodedValue) {
        if (encodedValue == null) {
            return null;
        }
        return new String(Base64.getDecoder().decode(encodedValue));
    }
}