package io.redlink.more.data.controller;

import io.redlink.more.data.api.app.v1.model.PushNotificationDTO;
import io.redlink.more.data.api.app.v1.webservices.NotificationsApi;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(value = "/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
public class NotificationApiV1Controller implements NotificationsApi {
    @Override
    public ResponseEntity<Void> deleteNotification(String msgId) {
        return null;
    }

    @Override
    public ResponseEntity<List<PushNotificationDTO>> listPushNotifications() {
        return ResponseEntity.ok(List.of(
                new PushNotificationDTO().msgId("id1").type(PushNotificationDTO.TypeEnum.TEXT).title("Hello").body("World"),
                new PushNotificationDTO().msgId("id2").type(PushNotificationDTO.TypeEnum.DATA).data(
                        Map.of("key", "STUDY_STATE_CHANGED", "oldState", "paused", "newState", "active")
                )
        ));
    }
}
