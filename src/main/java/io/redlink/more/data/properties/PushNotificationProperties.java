package io.redlink.more.data.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

@ConfigurationProperties(prefix = "push-notifications")
public record PushNotificationProperties(
        FcmConfigurationProperties fcm
) {


    public record FcmConfigurationProperties(
            Resource googleServiceJson,
            String projectId,
            String applicationId,
            String apiKey,
            String databaseUrl,
            String gcmSenderId,
            String storageBucket
    ) {
    }

}
