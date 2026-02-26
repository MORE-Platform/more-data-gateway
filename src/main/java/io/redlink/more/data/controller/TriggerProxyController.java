/*
 * Copyright LBI-DHP and/or licensed to LBI-DHP under one or more
 * contributor license agreements (LBI-DHP: Ludwig Boltzmann Institute
 * for Digital Health and Prevention -- A research institute of the
 * Ludwig Boltzmann Gesellschaft, Oesterreichische Vereinigung zur
 * Foerderung der wissenschaftlichen Forschung).
 * Licensed under the Elastic License 2.0.
 */
package io.redlink.more.data.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.redlink.more.data.model.ResolvedInterventionToken;
import io.redlink.more.data.service.ExternalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@RestController
public class TriggerProxyController {

    private static final Logger LOG = LoggerFactory.getLogger(TriggerProxyController.class);

    private final RestTemplate restTemplate;
    private final String studymanagerUrl;
    private final ExternalService externalService;
    private final ObjectMapper objectMapper;

    public TriggerProxyController(
            @Value("${more.studymanager.url:http://localhost:8080}") String studymanagerUrl,
            ExternalService externalService,
            ObjectMapper objectMapper
    ) {
        this.restTemplate = new RestTemplate();
        this.studymanagerUrl = studymanagerUrl;
        this.externalService = externalService;
        this.objectMapper = objectMapper;
    }

    @PostMapping(value = "/api/v1/trigger/external", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> triggerExternal(
            @RequestHeader("More-Api-Token") String moreApiToken,
            @RequestBody TriggerRequest request
    ) {
        LOG.info("Validating intervention token and proxying trigger request to studymanager");

        try {
            ResolvedInterventionToken resolved = externalService.validateInterventionToken(moreApiToken);

            if (!resolved.studyActive()) {
                LOG.warn("Trigger rejected: study {} is not active", resolved.studyId());
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Study not active"));
            }

            var proxyBody = objectMapper.writeValueAsString(Map.of(
                    "studyId", resolved.studyId(),
                    "interventionId", resolved.interventionId(),
                    "participantIds", request.participantIds()
            ));

            var headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            var httpRequest = new org.springframework.http.HttpEntity<>(proxyBody, headers);
            restTemplate.postForEntity(
                    studymanagerUrl + "/api/v1/trigger/external",
                    httpRequest,
                    Void.class
            );
            return ResponseEntity.accepted().build();
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (Exception e) {
            LOG.error("Error processing external trigger request", e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }

    public record TriggerRequest(List<Integer> participantIds) {}
}
