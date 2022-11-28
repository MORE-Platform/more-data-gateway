/*
 * Copyright (c) 2022 Redlink GmbH.
 */
package io.redlink.more.data.controller.transformer;

import io.redlink.more.data.api.app.v1.model.FcmNotificationConfigDTO;
import io.redlink.more.data.api.app.v1.model.PushNotificationServiceTypeDTO;
import io.redlink.more.data.service.PushNotificationService;

public final class NotificationServiceTransformer {

    private NotificationServiceTransformer() {}

    public static FcmNotificationConfigDTO toDTO(PushNotificationService.FcmConfiguration fcmConfig) {
        final FcmNotificationConfigDTO dto = new FcmNotificationConfigDTO()
                .projectId(fcmConfig.projectId())
                .applicationId(fcmConfig.applicationId())
                .apiKey(fcmConfig.apiKey())
                .storageBucket(fcmConfig.storageBucket())
                .gcmSenderId(fcmConfig.gcmSenderId())
                ;
        dto.setService(PushNotificationServiceTypeDTO.FCM);
        return dto;
    }
}
