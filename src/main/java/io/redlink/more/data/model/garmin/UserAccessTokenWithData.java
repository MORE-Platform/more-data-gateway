package io.redlink.more.data.model.garmin;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public record UserAccessTokenWithData(
        GarminUserAccessToken accessToken,
        Instant createdAt
) {

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("createdAt", createdAt.toEpochMilli());
        map.put("accessToken", accessToken.toMap());
        return map;
    }

    public String getAccessToken() {
        return accessToken.accessToken();
    }

    public Boolean isAccessTokenExpired() {
        var now = Instant.now();
        return now.getEpochSecond() - createdAt.getEpochSecond() > accessToken.expiresIn();
    }

    public Boolean isRefreshAccessTokenExpired() {
        var now = Instant.now();
        return now.getEpochSecond() - createdAt.getEpochSecond() > accessToken.refreshTokenExpiresIn();
    }

    public Boolean isAccessTokenValidOrRefreshable() {
        return !getAccessToken().isEmpty() && !isAccessTokenExpired() || !isRefreshAccessTokenExpired();
    }

    public static UserAccessTokenWithData fromMap(Map<String, Object> map) {
        return new UserAccessTokenWithData(
                GarminUserAccessToken.fromMap((Map<String, Object>) map.get("accessToken")),
                Instant.ofEpochMilli((Long) map.get("createdAt"))
        );
    }

    public static UserAccessTokenWithData createNewFrom(GarminUserAccessToken accessToken) {
        return new UserAccessTokenWithData(accessToken, Instant.now());
    }
}
