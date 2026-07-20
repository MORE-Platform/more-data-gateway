package io.redlink.more.data.controller;

import io.redlink.more.data.api.app.v1.webservices.GarminRegistrationApi;
import io.redlink.more.data.configuration.AuthenticationFacade;
import io.redlink.more.data.model.GatewayUserDetails;
import io.redlink.more.data.service.GatewayUserDetailService;
import io.redlink.more.data.service.garmin.GarminService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;


@Controller
@RestController
@RequestMapping(value = "/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
public class GarminRegistrationApiV1Controller implements GarminRegistrationApi {

    private static final Logger LOG = LoggerFactory.getLogger(GarminRegistrationApiV1Controller.class);

    private final GarminService garminService;
    private final AuthenticationFacade authenticationFacade;

    public GarminRegistrationApiV1Controller(GarminService garminService, AuthenticationFacade authenticationFacade) {
        this.garminService = garminService;
        this.authenticationFacade = authenticationFacade;
    }

    @Override
    public ResponseEntity<Void> getGarminOauthUrl() {
        String baseUrl = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .build()
                .toUriString();
        final GatewayUserDetails userDetails = this.authenticationFacade.assertAuthority(GatewayUserDetailService.APP_ROLE);
        if (userDetails == null) {
            LOG.warn("Garmin OAuth URL requested without authentication");
            throw new AccessDeniedException("Authentication required");
        }
        if (!userDetails.getRoutingInfo().acceptData()) {
            LOG.warn("Garmin OAuth URL requested for inactive study/participant: studyId={}, participantId={}", userDetails.getRoutingInfo().studyId(), userDetails.getRoutingInfo().participantId());
            throw new AccessDeniedException("Study or participant not active");
        }

        LOG.info("Generating Garmin OAuth URL for studyId={}, participantId={}", userDetails.getRoutingInfo().studyId(), userDetails.getRoutingInfo().participantId());
        String redirectUrl = garminService.getSsoUrl(userDetails.getRoutingInfo(), baseUrl + "/callback");
        LOG.debug("Redirecting to Garmin OAuth URL: {}", redirectUrl);
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, redirectUrl)
                .build();
    }

    @Override
    public ResponseEntity<Void> handleGarminCallback(String code, String state) {
        LOG.info("Handling Garmin OAuth callback: statePresent={}, codePresent={}", state != null, code != null);
        garminService.ssoCallback(state, code);
        return ResponseEntity.ok().build();
    }
}
