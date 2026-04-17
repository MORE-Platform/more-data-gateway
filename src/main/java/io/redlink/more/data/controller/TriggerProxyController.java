/*
 * Copyright LBI-DHP and/or licensed to LBI-DHP under one or more
 * contributor license agreements (LBI-DHP: Ludwig Boltzmann Institute
 * for Digital Health and Prevention -- A research institute of the
 * Ludwig Boltzmann Gesellschaft, Oesterreichische Vereinigung zur
 * Foerderung der wissenschaftlichen Forschung).
 * Licensed under the Elastic License 2.0.
 */
package io.redlink.more.data.controller;

import io.redlink.more.data.model.ResolvedInterventionToken;
import io.redlink.more.data.repository.StudyRepository;
import io.redlink.more.data.service.ExternalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class TriggerProxyController {

    private static final Logger LOG = LoggerFactory.getLogger(TriggerProxyController.class);
    static final String PENDING_PARTICIPANTS_KEY = "pendingParticipants";

    private final StudyRepository studyRepository;
    private final ExternalService externalService;

    public TriggerProxyController(StudyRepository studyRepository, ExternalService externalService) {
        this.studyRepository = studyRepository;
        this.externalService = externalService;
    }

    @PostMapping(value = "/api/v1/trigger/external", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> triggerExternal(
            @RequestHeader("More-Api-Token") String moreApiToken,
            @RequestBody TriggerRequest request
    ) {
        LOG.info("Validating intervention token and writing pending participants to DB");

        try {
            ResolvedInterventionToken resolved = externalService.validateInterventionToken(moreApiToken);

            if (!resolved.studyActive()) {
                LOG.warn("Trigger rejected: study {} is not active", resolved.studyId());
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Study not active"));
            }

            studyRepository.addPendingTriggerParticipants(
                    resolved.studyId(),
                    resolved.interventionId(),
                    PENDING_PARTICIPANTS_KEY,
                    request.participantIds()
            );

            return ResponseEntity.accepted().build();
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (Exception e) {
            LOG.error("Error processing external trigger request", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    public record TriggerRequest(List<Integer> participantIds) {}
}
