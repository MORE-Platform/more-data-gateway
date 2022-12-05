/*
 * Copyright (c) 2022 Redlink GmbH.
 */
package io.redlink.more.data.controller;

import io.redlink.more.data.api.app.v1.model.ApiKeyDTO;
import io.redlink.more.data.api.app.v1.model.AppConfigurationDTO;
import io.redlink.more.data.api.app.v1.model.ObservationConsentDTO;
import io.redlink.more.data.api.app.v1.model.StudyConsentDTO;
import io.redlink.more.data.api.app.v1.model.StudyDTO;
import io.redlink.more.data.api.app.v1.webservices.RegistrationApi;
import io.redlink.more.data.configuration.AuthenticationFacade;
import io.redlink.more.data.controller.transformer.StudyTransformer;
import io.redlink.more.data.model.ApiCredentials;
import io.redlink.more.data.model.GatewayUserDetails;
import io.redlink.more.data.model.ParticipantConsent;
import io.redlink.more.data.properties.MoreProperties;
import io.redlink.more.data.service.GatewayUserDetailService;
import io.redlink.more.data.service.RegistrationService;
import java.net.URI;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Controller
@RestController
@RequestMapping(value = "/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
@EnableConfigurationProperties(MoreProperties.class)
public class RegistrationApiV1Controller implements RegistrationApi {

    private final MoreProperties moreProperties;

    private final RegistrationService registrationService;

    private final AuthenticationFacade authenticationFacade;


    public RegistrationApiV1Controller(MoreProperties moreProperties, RegistrationService registrationService, AuthenticationFacade authenticationFacade) {
        this.moreProperties = moreProperties;
        this.registrationService = registrationService;
        this.authenticationFacade = authenticationFacade;
    }

    @Override
    public ResponseEntity<StudyDTO> getStudyRegistrationInfo(String moreRegistrationToken) {
        return registrationService.loadStudyByRegistrationToken(moreRegistrationToken)
                .map(StudyTransformer::toDTO)
                .map(study -> ResponseEntity.ok()
                        // For better debugging: return the token for chaining
                        .header("More-Registration-Token", moreRegistrationToken)
                        .body(study)
                )
                .orElseGet(() -> ResponseEntity.notFound().build())
                ;
    }

    @Override
    public ResponseEntity<AppConfigurationDTO> registerForStudy(String moreRegistrationToken, StudyConsentDTO studyConsentDTO) {
        final ParticipantConsent consent = convert(studyConsentDTO);
        try {
            if (registrationService.validateConsent(consent)) {
                return ResponseEntity.of(
                        registrationService.register(moreRegistrationToken, consent)
                                .map(RegistrationApiV1Controller::convert)
                                .map(cred -> new AppConfigurationDTO()
                                        .credentials(cred)
                                        .endpoint(getBaseURI())
                                )
                );
            }

            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .header("X-Info", "Consent not given")
                    .build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .header("X-Info", e.getMessage())
                    .build();
        }
    }

    @Override
    public ResponseEntity<Void> unregisterFromStudy() {
        final GatewayUserDetails userDetails = authenticationFacade
                .assertAuthority(GatewayUserDetailService.APP_ROLE);

        registrationService.unregister(userDetails.getUsername(), userDetails.getRoutingInfo());

        return ResponseEntity.noContent().build();
    }

    private URI getBaseURI() {
        if (moreProperties.gateway().baseUrl() != null && moreProperties.gateway().baseUrl().isAbsolute()) {
            return moreProperties.gateway().baseUrl();
        } else {
            return ServletUriComponentsBuilder.fromCurrentRequest()
                    .pathSegment("..")
                    .build()
                    .normalize()
                    .toUri();
        }
    }

    private static ParticipantConsent convert(StudyConsentDTO dto) {
        return new ParticipantConsent(
                dto.getConsent(),
                dto.getDeviceId(),
                dto.getConsentInfoMD5(),
                null,
                convert(dto.getObservations())
        );
    }

    private static List<ParticipantConsent.ObservationConsent> convert(List<ObservationConsentDTO> observations) {
        return observations.stream()
                .map(RegistrationApiV1Controller::convert)
                .toList();
    }

    private static ParticipantConsent.ObservationConsent convert(ObservationConsentDTO observations) {
        return new ParticipantConsent.ObservationConsent(
                Integer.parseInt(observations.getObservationId()),
                null
        );
    }

    private static ApiKeyDTO convert(ApiCredentials credentials) {
        return new ApiKeyDTO()
                .apiId(credentials.apiId())
                .apiKey(credentials.apiSecret());
    }
}
