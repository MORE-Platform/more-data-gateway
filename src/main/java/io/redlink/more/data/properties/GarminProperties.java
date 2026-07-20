package io.redlink.more.data.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.scheduling.support.CronTrigger;

import java.net.URI;
import java.util.Base64;

@ConfigurationProperties(prefix = "garmin")
public record GarminProperties(
        OAuth oauth,
        CronTrigger tokenRefresh
) {
    public record OAuth(
            String garminOauthUrl,
            String garminAuthorizeUrl,
            String clientId,
            String clientSecret,
            String garminCallbackRedirectUri,
            String redirectUrl
    ) {
    }


    public URI basicOAuthUri(String redirectUri) {
        if (oauth() == null || oauth().garminOauthUrl() == null || oauth().garminOauthUrl().isEmpty()) {
            throw new IllegalStateException("Missing Garmin OAuth URL");
        }
        if (oauth().clientId() == null || oauth().clientId().isEmpty()) {
            throw new IllegalStateException("Missing Garmin Client ID");
        }
        String baseUri = oauth().garminOauthUrl()
                + "?client_id=" + oauth().clientId()
                + "&response_type=code";
        if (oauth().garminCallbackRedirectUri() != null && !oauth().garminCallbackRedirectUri().isEmpty()) {
            baseUri += "&redirect_uri=" + (redirectUri != null ? redirectUri : oauth().garminCallbackRedirectUri());
        }
        return URI.create(baseUri);
    }

    public URI garminTokenUri() {
        if (oauth() == null || oauth().garminAuthorizeUrl() == null || oauth().garminAuthorizeUrl().isEmpty()) {
            throw new IllegalStateException("Missing Garmin Token URL");
        }
        return URI.create(oauth().garminAuthorizeUrl());
    }

    public String getRedirectUri() {
        return oauth().redirectUrl();
    }

    public String authorizationHeader() {
        return "Basic " + Base64.getEncoder().encodeToString((oauth().clientId() + ":" + oauth().clientSecret()).getBytes());
    }

    public Boolean clientIdsMatch(String cliendId) {
        return oauth().clientId().equalsIgnoreCase(cliendId);
    }
}
