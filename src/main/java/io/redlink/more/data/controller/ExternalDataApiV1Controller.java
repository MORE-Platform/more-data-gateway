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

import java.util.List;

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
        final ApiRoutingInfo apiRoutingInfo = externalService.getRoutingInfo(
                moreApiToken,
                endpointDataBulkDTO.getParticipantId());
        if(apiRoutingInfo == null) {
            throw new AccessDeniedException("Invalid token");
        }
        final RoutingInfo routingInfo = new RoutingInfo(
                apiRoutingInfo.studyId(),
                apiRoutingInfo.participantId(),
                apiRoutingInfo.rawStudyGroupId(),
                apiRoutingInfo.studyActive()
        );
        try (LoggingUtils.LoggingContext ctx = LoggingUtils.createContext(routingInfo)) {
            if(routingInfo.studyActive()) {
                elasticService.storeDataPoints(
                        DataTransformer.createDataPoints(endpointDataBulkDTO, apiRoutingInfo),
                        routingInfo
                );
            } else {
                final List<String> discardedIDs = endpointDataBulkDTO.getDataPoints().stream()
                        .map(ExternalDataDTO::getDataId)
                        .toList();
                LOG.info("Discarding {} observations because study_{} is not 'active'",
                        discardedIDs.size(), routingInfo.studyId());
            }
            return ResponseEntity.noContent().build();
        }
    }
}
