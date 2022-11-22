package io.redlink.more.data.controller;

import io.redlink.more.data.api.StorageService;
import io.redlink.more.data.api.app.v1.model.DataBulkDTO;
import io.redlink.more.data.api.app.v1.webservices.DataApi;
import io.redlink.more.data.configuration.AuthenticationFacade;
import io.redlink.more.data.controller.transformer.DataTransformer;
import io.redlink.more.data.model.GatewayUserDetails;
import io.redlink.more.data.model.RoutingInfo;
import io.redlink.more.data.service.ElasticService;
import io.redlink.more.data.service.GatewayUserDetailService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Controller
@RestController
@RequestMapping(value = "/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
public class DataApiV1Controller implements DataApi {

    private final AuthenticationFacade authenticationFacade;

    private final StorageService elasticService;


    DataApiV1Controller(AuthenticationFacade authenticationFacade, ElasticService elasticService) {
        this.authenticationFacade = authenticationFacade;
        this.elasticService = elasticService;
    }

    @Override
    public ResponseEntity<List<String>> storeBulk(DataBulkDTO dataBulkDTO) {
        final GatewayUserDetails userDetails = this.authenticationFacade
                .assertAuthority(GatewayUserDetailService.APP_ROLE);
        if (userDetails == null) {
            throw new AccessDeniedException("Authentication required");
        }

        final RoutingInfo routingInfo = userDetails.getRoutingInfo();
        final List<String> storedIDs = elasticService.storeDataPoints(
                DataTransformer.createDataPoints(dataBulkDTO), routingInfo);
        return ResponseEntity.status(HttpStatus.OK).body(storedIDs);
    }

}
