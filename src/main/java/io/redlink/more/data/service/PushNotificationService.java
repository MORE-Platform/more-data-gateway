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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.defaultString;

@Service
@EnableConfigurationProperties(PushNotificationProperties.class)
public class PushNotificationService {

    private static final Logger LOG = LoggerFactory.getLogger(PushNotificationService.class);

    private final FcmConfiguration fcmConfiguration;

    private final PushTokenRepository pushTokenRepository;

    public PushNotificationService(PushNotificationProperties notificationProps, PushTokenRepository pushTokenRepository) {
        this.fcmConfiguration = FcmConfiguration.load(notificationProps.fcm());
        this.pushTokenRepository = pushTokenRepository;

        if (fcmConfiguration == null) {
           LOG.warn("No FCM Configuration available, you might want to set 'push-notifications.fcm.*'");
        }
    }

    public boolean hasFcmConfig() {
        return getFcmConfig() != null;
    }

    public FcmConfiguration getFcmConfig() {
        return fcmConfiguration;
    }

    public void storeFcmToken(RoutingInfo userDetails, String token) {
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
            if (props == null) return null;

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
                    requireNonNull(
                            defaultString(props.projectId(), readString(gsJson, "/project_info/project_id")),
                            "projectId must be configured"
                    ),
                    requireNonNull(
                            defaultString(props.applicationId(), readString(gsJson, "/client/0/client_info/mobilesdk_app_id")),
                            "applicationId must be configured"
                    ),
                    requireNonNull(
                            defaultString(props.apiKey(), readString(gsJson, "/client/0/api_key/0/current_key")),
                            "apiKey must be configured"
                    ),
                    requireNonNull(
                            defaultString(props.gcmSenderId(), readString(gsJson, "/project_info/project_number")),
                            "gcmSenderId must be configured"
                    ),
                    requireNonNull(
                            defaultString(props.storageBucket(), readString(gsJson, "/project_info/storage_bucket")),
                            "storageBucket must be configured"
                    )
            );
        }

        private static String readString(JsonNode node, String path) {
            return node.at(path).asText(null);
        }

    }

}
