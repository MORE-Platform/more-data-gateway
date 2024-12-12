/*
 * Copyright LBI-DHP and/or licensed to LBI-DHP under one or more
 * contributor license agreements (LBI-DHP: Ludwig Boltzmann Institute
 * for Digital Health and Prevention -- A research institute of the
 * Ludwig Boltzmann Gesellschaft, Oesterreichische Vereinigung zur
 * Foerderung der wissenschaftlichen Forschung).
 * Licensed under the Elastic License 2.0.
 */
package io.redlink.more.data.controller;

import io.redlink.more.data.api.app.v1.model.EndpointDataBulkDTO;
import io.redlink.more.data.api.app.v1.model.ParticipantDTO;
import io.redlink.more.data.api.app.v1.webservices.ExternalDataApi;
import io.redlink.more.data.controller.transformer.DataTransformer;
import io.redlink.more.data.controller.transformer.ParticipantTransformer;
import io.redlink.more.data.exception.BadRequestException;
import io.redlink.more.data.exception.TimeFrameException;
import io.redlink.more.data.model.ApiRoutingInfo;
import io.redlink.more.data.model.RoutingInfo;
import io.redlink.more.data.service.ElasticService;
import io.redlink.more.data.service.ExternalService;
import io.redlink.more.data.util.LoggingUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(value = "/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
public class ExternalDataApiV1Controller implements ExternalDataApi {

    private static final Logger LOG = LoggerFactory.getLogger(ExternalDataApiV1Controller.class);

    private final ExternalService externalService;
    private final ElasticService elasticService;

    public ExternalDataApiV1Controller(ExternalService externalService, ElasticService elasticService) {
        this.externalService = externalService;
        this.elasticService = elasticService;
    }

    @Override
    public ResponseEntity<List<ParticipantDTO>> listParticipants(String moreApiToken) {
        try {
            ApiRoutingInfo apiRoutingInfo = externalService.getRoutingInfo(moreApiToken);
            return ResponseEntity.ok(
                    externalService.listParticipants(apiRoutingInfo.studyId(), apiRoutingInfo.studyGroupId())
                            .stream()
                            .map(ParticipantTransformer::toDTO)
                            .toList()
            );
        } catch (IndexOutOfBoundsException | NumberFormatException e) {
            throw new AccessDeniedException("Invalid Token");
        }
    }

    @Override
    public ResponseEntity<String> storeExternalBulk(String moreApiToken, EndpointDataBulkDTO endpointDataBulkDTO) {
        try {
            ApiRoutingInfo apiRoutingInfo = externalService.getRoutingInfo(moreApiToken);
            Integer participantId = Integer.valueOf(endpointDataBulkDTO.getParticipantId());

            externalService.allTimestampsInBulkAreValid(apiRoutingInfo.studyId(), apiRoutingInfo.observationId(), participantId, endpointDataBulkDTO);

            final RoutingInfo routingInfo = externalService.validateAndCreateRoutingInfo(apiRoutingInfo, participantId);
            LoggingUtils.createContext(routingInfo);
            if (routingInfo.acceptData()) {
                elasticService.storeDataPoints(
                        DataTransformer.createDataPoints(endpointDataBulkDTO, apiRoutingInfo, apiRoutingInfo.observationId()),
                        routingInfo
                );
            } else {
                final int numberOfDiscardedIds = endpointDataBulkDTO.getDataPoints().size();
                LOG.info("Discarding {} data-points because either study_{} or participant_{} is not 'active'",
                        numberOfDiscardedIds, routingInfo.studyId(), routingInfo.participantId());
                return ResponseEntity.status(HttpStatus.CONFLICT).body("Study or participant is not active");
            }
            return ResponseEntity.accepted().body("Data has been successfully processed.");
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Invalid token!");
        } catch (NumberFormatException e) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body("Malformed participant id!");
        } catch (TimeFrameException e) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(e.getMessage());
        } catch (BadRequestException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
