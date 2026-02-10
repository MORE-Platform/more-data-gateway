/*
 * Copyright LBI-DHP and/or licensed to LBI-DHP under one or more
 * contributor license agreements (LBI-DHP: Ludwig Boltzmann Institute
 * for Digital Health and Prevention -- A research institute of the
 * Ludwig Boltzmann Gesellschaft, Oesterreichische Vereinigung zur
 * Foerderung der wissenschaftlichen Forschung).
 * Licensed under the Elastic License 2.0.
 */
package io.redlink.more.data.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

@RestController
public class TriggerProxyController {

    private static final Logger LOG = LoggerFactory.getLogger(TriggerProxyController.class);

    private final RestTemplate restTemplate;
    private final String studymanagerUrl;

    public TriggerProxyController(
            @Value("${more.studymanager.url:http://localhost:8080}") String studymanagerUrl
    ) {
        this.restTemplate = new RestTemplate();
        this.studymanagerUrl = studymanagerUrl;
    }

    @PostMapping(value = "/api/v1/trigger/external", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> triggerExternal(
            @RequestHeader("More-Api-Token") String moreApiToken,
            @RequestBody String body
    ) {
        LOG.info("Proxying external trigger request to studymanager");

        try {
            var headers = new org.springframework.http.HttpHeaders();
            headers.set("More-Api-Token", moreApiToken);
            headers.setContentType(MediaType.APPLICATION_JSON);

            var request = new org.springframework.http.HttpEntity<>(body, headers);
            restTemplate.postForEntity(
                    studymanagerUrl + "/api/v1/trigger/external",
                    request,
                    Void.class
            );
            return ResponseEntity.accepted().build();
        } catch (HttpClientErrorException.Forbidden e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (Exception e) {
            LOG.error("Error proxying trigger request", e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }
}
