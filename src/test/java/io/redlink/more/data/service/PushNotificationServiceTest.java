package io.redlink.more.data.service;

import io.redlink.more.data.properties.PushNotificationProperties;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PushNotificationServiceTest {


    @Test
    void testLoadFcmConfigFromJson() {
        var prop = new PushNotificationProperties.FcmConfigurationProperties(
                new ClassPathResource("test-google-services.json"),
                null,
                null,
                null,
                null,
                null
        );

        var config = PushNotificationService.FcmConfiguration.load(prop);
        assertNotNull(config, "Loading Failed");

        assertThat("projectId from json", config.projectId(), equalTo("test-project-id"));
        assertThat("applicationId from json", config.applicationId(), equalTo("test-application-id"));
        assertThat("apiKey from json", config.apiKey(), equalTo("test-api-key"));
        assertThat("gcmSenderId from json", config.gcmSenderId(), equalTo("test-gcm-sender-id"));
        assertThat("storageBucket from json", config.storageBucket(), equalTo("test-storage-bucket"));
    }

    @Test
    void testLoadFcmConfigFromJsonWithOverrides() {
        var apiKey = UUID.randomUUID().toString();
        var prop = new PushNotificationProperties.FcmConfigurationProperties(
                new ClassPathResource("test-google-services.json"),
                null,
                null,
                apiKey,
                null,
                null
        );

        var config = PushNotificationService.FcmConfiguration.load(prop);
        assertNotNull(config, "Loading Failed");

        assertThat("projectId from json", config.projectId(), equalTo("test-project-id"));
        assertThat("applicationId from json", config.applicationId(), equalTo("test-application-id"));
        assertThat("apiKey from properties", config.apiKey(), equalTo(apiKey));
        assertThat("gcmSenderId from json", config.gcmSenderId(), equalTo("test-gcm-sender-id"));
        assertThat("storageBucket from json", config.storageBucket(), equalTo("test-storage-bucket"));

    }

    @Test
    void testCreateFcmConfig() {
        var apiKey = UUID.randomUUID().toString();
        var prop = new PushNotificationProperties.FcmConfigurationProperties(
                null,
                "test-project-id",
                "test-application-id",
                apiKey,
                "test-gcm-sender-id",
                "test-storage-bucket"
        );

        var config = PushNotificationService.FcmConfiguration.load(prop);
        assertNotNull(config, "Loading Failed");

        assertThat("projectId from json", config.projectId(), equalTo("test-project-id"));
        assertThat("applicationId from json", config.applicationId(), equalTo("test-application-id"));
        assertThat("apiKey from json", config.apiKey(), equalTo(apiKey));
        assertThat("gcmSenderId from json", config.gcmSenderId(), equalTo("test-gcm-sender-id"));
        assertThat("storageBucket from json", config.storageBucket(), equalTo("test-storage-bucket"));

    }
}