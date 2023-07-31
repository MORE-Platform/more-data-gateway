package io.redlink.more.data.controller;

import io.redlink.more.data.api.app.v1.model.PushNotificationDTO;
import io.redlink.more.data.api.app.v1.webservices.NotificationsApi;
import io.redlink.more.data.configuration.AuthenticationFacade;
import io.redlink.more.data.model.GatewayUserDetails;
import io.redlink.more.data.model.RoutingInfo;
import io.redlink.more.data.repository.NotificationRepository;
import io.redlink.more.data.service.GatewayUserDetailService;
import io.redlink.more.data.util.LoggingUtils;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(value = "/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
public class NotificationApiV1Controller implements NotificationsApi {

    private final AuthenticationFacade authenticationFacade;
    private final NotificationRepository repository;

    public NotificationApiV1Controller(AuthenticationFacade authenticationFacade, NotificationRepository repository) {
        this.authenticationFacade = authenticationFacade;
        this.repository = repository;
    }

    @Override
    public ResponseEntity<Void> deleteNotification(String msgId) {
        final GatewayUserDetails userDetails = this.authenticationFacade
                .assertAuthority(GatewayUserDetailService.APP_ROLE);
        if (userDetails == null) {
            throw new AccessDeniedException("Authentication required");
        }

        final RoutingInfo routingInfo = userDetails.getRoutingInfo();

        if(this.repository.delete(routingInfo.studyId(), routingInfo.participantId(), msgId) > 0) {
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @Override
    public ResponseEntity<List<PushNotificationDTO>> listPushNotifications() {

        final GatewayUserDetails userDetails = this.authenticationFacade
                .assertAuthority(GatewayUserDetailService.APP_ROLE);
        if (userDetails == null) {
            throw new AccessDeniedException("Authentication required");
        }

        final RoutingInfo routingInfo = userDetails.getRoutingInfo();

        return ResponseEntity.ok(
                repository.listAndDeleteFor(routingInfo.studyId(), routingInfo.participantId())
        );
    }
}
