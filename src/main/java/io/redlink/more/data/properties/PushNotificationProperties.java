/*
 * Copyright LBI-DHP and/or licensed to LBI-DHP under one or more
 * contributor license agreements (LBI-DHP: Ludwig Boltzmann Institute
 * for Digital Health and Prevention -- A research institute of the
 * Ludwig Boltzmann Gesellschaft, Oesterreichische Vereinigung zur
 * Foerderung der wissenschaftlichen Forschung).
 * Licensed under the Elastic License 2.0.
 */
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
            String gcmSenderId,
            String storageBucket
    ) {
    }

}
