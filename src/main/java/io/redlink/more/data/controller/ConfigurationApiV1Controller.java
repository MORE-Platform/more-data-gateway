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
import io.redlink.more.data.controller.transformer.StudyTransformer;
import io.redlink.more.data.model.GatewayUserDetails;
import io.redlink.more.data.service.GatewayUserDetailService;
import io.redlink.more.data.service.RegistrationService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Controller
@RestController
@RequestMapping(value = "/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
public class ConfigurationApiV1Controller implements ConfigurationApi {

    private final AuthenticationFacade authenticationFacade;

    private final RegistrationService registrationService;

    public ConfigurationApiV1Controller(AuthenticationFacade authenticationFacade, RegistrationService registrationService) {
        this.authenticationFacade = authenticationFacade;
        this.registrationService = registrationService;
    }

    @Override
    public ResponseEntity<PushNotificationConfigDTO> getPushNotificationServiceClientConfig(PushNotificationServiceTypeDTO serviceType) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @Override
    public ResponseEntity<StudyDTO> getStudyConfiguration() {
        final GatewayUserDetails userDetails = authenticationFacade
                .assertAuthority(GatewayUserDetailService.APP_ROLE);

        return ResponseEntity.of(
                registrationService.loadStudyByRoutingInfo(userDetails.getRoutingInfo())
                        .map(StudyTransformer::toDTO)
        );
    }

    @Override
    public ResponseEntity<List<PushNotificationServiceTypeDTO>> listPushNotificationServices() {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @Override
    public ResponseEntity<Void> setPushNotificationToken(PushNotificationServiceTypeDTO serviceType, PushNotificationTokenDTO pushNotificationTokenDTO) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }
}
