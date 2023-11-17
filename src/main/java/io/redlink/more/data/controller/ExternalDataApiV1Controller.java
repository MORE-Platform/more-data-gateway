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
import io.redlink.more.data.api.app.v1.model.ExternalDataDTO;
import io.redlink.more.data.api.app.v1.webservices.ExternalDataApi;
import io.redlink.more.data.controller.transformer.DataTransformer;
import io.redlink.more.data.model.ApiRoutingInfo;
import io.redlink.more.data.model.RoutingInfo;
import io.redlink.more.data.service.ElasticService;
import io.redlink.more.data.service.ExternalService;
import io.redlink.more.data.util.LoggingUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Base64;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping(value = "/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
public class ExternalDataApiV1Controller implements ExternalDataApi {

    private static final Logger LOG = LoggerFactory.getLogger(DataApiV1Controller.class);
    private final ExternalService externalService;
    private final ElasticService elasticService;

    public ExternalDataApiV1Controller(ExternalService externalService, ElasticService elasticService) {
        this.externalService = externalService;
        this.elasticService = elasticService;
    }

    @Override
    public ResponseEntity<Void> storeExternalBulk(String moreApiToken, EndpointDataBulkDTO endpointDataBulkDTO) {
        try {
            String[] split = moreApiToken.split("\\.");
            String[] primaryKey = new String(Base64.getDecoder().decode(split[0])).split("-");

            Long studyId = Long.valueOf(primaryKey[0]);
            Integer observationId = Integer.valueOf(primaryKey[1]);
            Integer participantId = Integer.valueOf(endpointDataBulkDTO.getParticipantId());
            Integer tokenId = Integer.valueOf(primaryKey[2]);
            String secret = new String(Base64.getDecoder().decode(split[1]));

            final Optional<ApiRoutingInfo> apiRoutingInfo = externalService.getRoutingInfo(
                    studyId,
                    observationId,
                    tokenId,
                    secret);
            if(apiRoutingInfo.isEmpty()) {
                throw new AccessDeniedException("Invalid token");
            }

            externalService.validateTimeFrame(studyId, observationId,
                    endpointDataBulkDTO.getDataPoints().stream().map(datapoint ->
                            datapoint.getTimestamp().toInstant()
                    ).toList());

            final RoutingInfo routingInfo = new RoutingInfo(
                    externalService.validateRoutingInfo(apiRoutingInfo.get(), participantId),
                    participantId
            );
            try (LoggingUtils.LoggingContext ctx = LoggingUtils.createContext(routingInfo)) {
                if(routingInfo.studyActive()) {
                    elasticService.storeDataPoints(
                            DataTransformer.createDataPoints(endpointDataBulkDTO, apiRoutingInfo.get(), observationId),
                            routingInfo
                    );
                } else {
                    final List<ExternalDataDTO> discardedIDs = endpointDataBulkDTO.getDataPoints();
                    LOG.info("Discarding {} observations because study_{} is not 'active'",
                            discardedIDs.size(), routingInfo.studyId());
                }
                return ResponseEntity.noContent().build();
            }
        } catch(IndexOutOfBoundsException | NumberFormatException e) {
            throw new AccessDeniedException("Invalid Token");
        }
    }
}
