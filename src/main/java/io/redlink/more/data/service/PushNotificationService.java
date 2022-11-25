/*
 * Copyright (c) 2022 Redlink GmbH.
 */
package io.redlink.more.data.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import io.redlink.more.data.model.RoutingInfo;
import io.redlink.more.data.properties.PushNotificationProperties;
import io.redlink.more.data.repository.PushTokenRepository;
import java.io.IOException;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.defaultString;

@Service
@EnableConfigurationProperties(PushNotificationProperties.class)
public class PushNotificationService {

    private final PushNotificationProperties notificationProps;

    private final FcmConfiguration fcmConfiguration;

    private final PushTokenRepository pushTokenRepository;

    public PushNotificationService(PushNotificationProperties notificationProps, PushTokenRepository pushTokenRepository) {
        this.notificationProps = notificationProps;
        this.fcmConfiguration = FcmConfiguration.load(notificationProps.fcm());
        this.pushTokenRepository = pushTokenRepository;
    }

    public boolean hasFcmConfig() {
        return getFcmConfig() != null;
    }

    private FcmConfiguration getFcmConfig() {
        return fcmConfiguration;
    }

    public void storeToken(RoutingInfo userDetails, String token) {
        pushTokenRepository.storeToken(userDetails.studyId(), userDetails.participantId(), "FCM", token);
    }


    public record FcmConfiguration(
            String projectId,
            String applicationId,
            String apiKey,
            String gcmSenderId,
            String storageBucket
    ) {
        public static FcmConfiguration load(PushNotificationProperties.FcmConfigurationProperties props) {
            final JsonNode gsJson;
            if (props.googleServiceJson() != null && props.googleServiceJson().isReadable()) {
                var mapper = new ObjectMapper();
                try {
                    gsJson = mapper.readTree(props.googleServiceJson().getInputStream());
                } catch (IOException e) {
                    throw new IllegalStateException("Could not read google-services-json", e);
                }
            } else {
                gsJson = NullNode.getInstance();
            }

            return new FcmConfiguration(
                    requireNonNull(defaultString(props.projectId(), readString(gsJson, "$.project_info.project_id"))),
                    requireNonNull(defaultString(props.applicationId(), readString(gsJson, "$.client.client_info.mobilesdk_app_id"))),
                    requireNonNull(defaultString(props.apiKey(), readString(gsJson, "$.client.api_key[0].current_key"))),
                    requireNonNull(defaultString(props.gcmSenderId(), readString(gsJson, "$.project_info.project_number"))),
                    requireNonNull(defaultString(props.storageBucket(), readString(gsJson, "$.project_info.storage_bucket")))
            );
        }

        private static String readString(JsonNode node, String path) {
            return null;
        }

    }

}
