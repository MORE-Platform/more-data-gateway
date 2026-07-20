/*
 * Copyright (c) 2022 Redlink GmbH.
 */
package io.redlink.more.data.controller;

import io.redlink.more.data.api.app.v1.model.PushNotificationConfigDTO;
import io.redlink.more.data.api.app.v1.model.PushNotificationServiceTypeDTO;
import io.redlink.more.data.api.app.v1.model.PushNotificationTokenDTO;
import io.redlink.more.data.api.app.v1.model.StudyDTO;
import io.redlink.more.data.api.app.v1.webservices.ConfigurationApi;
import io.redlink.more.data.configuration.AuthenticationFacade;
import io.redlink.more.data.controller.transformer.NotificationServiceTransformer;
import io.redlink.more.data.controller.transformer.StudyTransformer;
import io.redlink.more.data.model.CompletedData;
import io.redlink.more.data.model.GatewayUserDetails;
import io.redlink.more.data.service.GatewayUserDetailService;
import io.redlink.more.data.service.ObservationExecutionService;
import io.redlink.more.data.service.PushNotificationService;
import io.redlink.more.data.service.StudyService;
import io.redlink.more.data.util.LoggingUtils;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Controller
@RestController
@RequestMapping(value = "/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
public class ConfigurationApiV1Controller implements ConfigurationApi {

    private final AuthenticationFacade authenticationFacade;

    private final PushNotificationService pushNotificationService;

    private final StudyService studyService;

    private final ObservationExecutionService observationExecutionService;

    public ConfigurationApiV1Controller(AuthenticationFacade authenticationFacade, PushNotificationService pushNotificationService, StudyService studyService, ObservationExecutionService observationExecutionService) {
        this.authenticationFacade = authenticationFacade;
        this.pushNotificationService = pushNotificationService;
        this.studyService = studyService;
        this.observationExecutionService = observationExecutionService;
    }

    @Override
    public ResponseEntity<StudyDTO> getStudyConfiguration() {
        final GatewayUserDetails userDetails = authenticationFacade
                .assertAuthority(GatewayUserDetailService.APP_ROLE);
        try (LoggingUtils.LoggingContext ctx = LoggingUtils.createContext(userDetails.getRoutingInfo())) {
            var studyData = studyService.getStudy(userDetails.getRoutingInfo());
            List<CompletedData> completedData = observationExecutionService.getCompletedData(userDetails.getRoutingInfo());
            return studyData.map(s -> StudyTransformer.toDTO(s.getLeft(), s.getRight(), completedData))
                    .map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.notFound().build());
        }
    }

    @Override
    public ResponseEntity<List<PushNotificationServiceTypeDTO>> listPushNotificationServices() {
        final GatewayUserDetails userDetails = authenticationFacade.assertAuthority(GatewayUserDetailService.APP_ROLE);

        try (LoggingUtils.LoggingContext ctx = LoggingUtils.createContext(userDetails.getRoutingInfo())) {
            if (pushNotificationService.hasFcmConfig()) {
                return ResponseEntity.ok(
                        List.of(PushNotificationServiceTypeDTO.FCM)
                );
            } else {
                return ResponseEntity.ok(List.of());
            }
        }
    }

    @Override
    public ResponseEntity<PushNotificationConfigDTO> getPushNotificationServiceClientConfig(PushNotificationServiceTypeDTO serviceType) {
        final GatewayUserDetails userDetails = authenticationFacade.assertAuthority(GatewayUserDetailService.APP_ROLE);

        try (LoggingUtils.LoggingContext ctx = LoggingUtils.createContext(userDetails.getRoutingInfo())) {
            if (serviceType == PushNotificationServiceTypeDTO.FCM && pushNotificationService.hasFcmConfig()) {
                return ResponseEntity.ok(
                        NotificationServiceTransformer.toDTO(
                                pushNotificationService.getFcmConfig()
                        )
                );
            } else {
                return ResponseEntity.badRequest().build();
            }
        }
    }

    @Override
    public ResponseEntity<Void> setPushNotificationToken(PushNotificationServiceTypeDTO serviceType, PushNotificationTokenDTO pushNotificationTokenDTO) {
        final GatewayUserDetails userDetails = authenticationFacade
                .assertAuthority(GatewayUserDetailService.APP_ROLE);

        try (LoggingUtils.LoggingContext ctx = LoggingUtils.createContext(userDetails.getRoutingInfo())) {
            if (serviceType == PushNotificationServiceTypeDTO.FCM) {
                pushNotificationService.storeFcmToken(userDetails.getRoutingInfo(), pushNotificationTokenDTO.getToken());
            } else {
                return ResponseEntity.badRequest().build();
            }
            return ResponseEntity.accepted().build();
        }
    }
}
