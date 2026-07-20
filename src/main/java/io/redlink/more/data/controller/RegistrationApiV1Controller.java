/*
 * Copyright (c) 2022 Redlink GmbH.
 */
package io.redlink.more.data.controller;

import io.redlink.more.data.api.app.v1.model.ApiKeyDTO;
import io.redlink.more.data.api.app.v1.model.AppConfigurationDTO;
import io.redlink.more.data.api.app.v1.model.StudyConsentDTO;
import io.redlink.more.data.api.app.v1.model.StudyDTO;
import io.redlink.more.data.api.app.v1.webservices.RegistrationApi;
import io.redlink.more.data.configuration.AuthenticationFacade;
import io.redlink.more.data.controller.transformer.StudyTransformer;
import io.redlink.more.data.exception.RegistrationNotPossibleException;
import io.redlink.more.data.model.ApiCredentials;
import io.redlink.more.data.model.GatewayUserDetails;
import io.redlink.more.data.model.ParticipantConsent;
import io.redlink.more.data.model.ParticipantObservationSeed;
import io.redlink.more.data.properties.MoreProperties;
import io.redlink.more.data.service.GatewayUserDetailService;
import io.redlink.more.data.service.RegistrationService;
import io.redlink.more.data.service.StudyService;
import io.redlink.more.data.util.ParticipantUtils;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.Collections;
import java.util.List;

@Controller
@RestController
@RequestMapping(value = "/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
@EnableConfigurationProperties(MoreProperties.class)
public class RegistrationApiV1Controller implements RegistrationApi {

    private final MoreProperties moreProperties;

    private final RegistrationService registrationService;

    private final AuthenticationFacade authenticationFacade;

    private final StudyService studyService;


    public RegistrationApiV1Controller(MoreProperties moreProperties, RegistrationService registrationService, AuthenticationFacade authenticationFacade, StudyService studyService) {
        this.moreProperties = moreProperties;
        this.registrationService = registrationService;
        this.authenticationFacade = authenticationFacade;
        this.studyService = studyService;
    }

    @Override
    public ResponseEntity<StudyDTO> getStudyRegistrationInfo(String moreRegistrationToken) {
        var study = registrationService.loadStudyByRegistrationToken(moreRegistrationToken);
        if (study.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        List<ParticipantObservationSeed> seeds = study.get().active() ? studyService.getParticipantObservationSeeds(study.get().studyId(), study.get().participant().id()) : Collections.emptyList();

        var studyDto = StudyTransformer.toDTO(study.get(), seeds, Collections.emptyList());
        return ResponseEntity.ok()
                // For better debugging: return the token for chaining
                .header("More-Registration-Token", moreRegistrationToken)
                .body(studyDto);

    }

    @Override
    public ResponseEntity<AppConfigurationDTO> registerForStudy(String moreRegistrationToken, StudyConsentDTO studyConsentDTO) {
        final ParticipantConsent consent = ParticipantUtils.convert(studyConsentDTO);

        if (consent.accepted()) {
            return ResponseEntity.of(
                    registrationService.register(moreRegistrationToken, consent)
                            .map(RegistrationApiV1Controller::convert)
                            .map(cred -> new AppConfigurationDTO()
                                    .credentials(cred)
                                    .endpoint(getBaseURI())
                            )
            );
        }

        throw RegistrationNotPossibleException.noConsentGiven();
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

    private static ApiKeyDTO convert(ApiCredentials credentials) {
        return new ApiKeyDTO()
                .apiId(credentials.apiId())
                .apiKey(credentials.apiSecret());
    }
}
