package io.redlink.more.data.controller;

import io.redlink.more.data.api.app.v1.model.DeregistrationRequestDTO;
import io.redlink.more.data.api.app.v1.model.UpdatePermissionsRequestDTO;
import io.redlink.more.data.api.app.v1.webservices.GarminUserManagementApi;
import io.redlink.more.data.custom.model.GarminDataPoint;
import io.redlink.more.data.model.Alias;
import io.redlink.more.data.model.garmin.GarminSummaryType;
import io.redlink.more.data.service.garmin.GarminService;
import io.redlink.more.data.util.MapperUtils;
import io.redlink.more.data.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RestController
@RequestMapping(value = "/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
public class GarminApiV1Controller implements GarminUserManagementApi {
    private static final Logger LOG = LoggerFactory.getLogger(GarminApiV1Controller.class);

    private final GarminService garminService;

    private final List<Alias> aliases = List.of(
            new Alias("measurementTimeInSeconds", "startTimeInSeconds"),
            new Alias("measurementTimeOffsetInSeconds", "startTimeOffsetInSeconds")
    );

    public GarminApiV1Controller(GarminService garminService) {
        this.garminService = garminService;
    }

    @Override
    public ResponseEntity<Void> deleteGarminUser(String userAgent, String garminClientId, DeregistrationRequestDTO deregistrationRequestDTO) {
        if (garminService.garminRequestIsValid(userAgent, garminClientId)) {
            deregistrationRequestDTO.getDeregistrations().forEach(deregistrationItemDTO -> {
                LOG.info("Handling Garmin user deregistration request for user: {}", deregistrationItemDTO.getUserId());
                garminService.deleteUserIdAndToken(deregistrationItemDTO.getUserId());
            });
        } else {
            LOG.warn("Invalid Garmin request: user agent: {}; client id: {}; deregistrationRequestDTO: {}", userAgent, StringUtils.anonymize(garminClientId), deregistrationRequestDTO);
        }
        return ResponseEntity.ok().build();
    }

    @Override
    public ResponseEntity<Void> updateGarminUserPermissions(String userAgent, String garminClientId, UpdatePermissionsRequestDTO updatePermissionsRequestDTO) {
        if (garminService.garminRequestIsValid(userAgent, garminClientId)) {
            updatePermissionsRequestDTO.getUserPermissionsChange()
                    .forEach(userPermissionChangeDTO -> {
                        LOG.info("Handling Garmin user permissions update request for user: {}", userPermissionChangeDTO.getUserId());
                        garminService.updateUserPermissions(userPermissionChangeDTO.getUserId(), userPermissionChangeDTO.getPermissions());
                    });
        } else {
            LOG.warn("Invalid Garmin request: user agent: {}; client id: {}; updatePermissionsRequestDTO: {}", userAgent, StringUtils.anonymize(garminClientId), updatePermissionsRequestDTO);
        }
        return ResponseEntity.ok().build();
    }

    @Override
    public ResponseEntity<Void> submitSummaries(String userAgent, String garminClientId, Map<String, List<Map<String, Object>>> requestBody) {
        if (garminService.garminRequestIsValid(userAgent, garminClientId)) {
            try {
                LOG.info("Summaries received: user agent: {}; client id: {}; summariesRequestDTO keys: {}", userAgent, StringUtils.anonymize(garminClientId), requestBody.keySet());
                Map<GarminSummaryType, List<GarminDataPoint>> requestDataPoints =
                        requestBody.entrySet()
                                .stream()
                                .filter(entry -> GarminSummaryType.fromLabel(entry.getKey()) != null)
                                .collect(Collectors.toMap(
                                        entry -> GarminSummaryType.fromLabel(entry.getKey()),
                                        entry -> ((List<?>) entry.getValue()).stream()
                                                .filter(item -> item instanceof Map && ((Map<?, ?>) item).containsKey("userId"))
                                                .map(item -> MapperUtils.convertValueWithAliases(
                                                        item,
                                                        GarminDataPoint.class,
                                                        aliases
                                                ))
                                                .collect(Collectors.toList())
                                ));
                garminService.storeData(requestDataPoints);
            } catch (ClassCastException e) {
                LOG.error("Exception casting Map<String, Object> to GarminDataPoints", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            } catch (IOException e) {
                LOG.error("IOException storing GarminDataPoints: {}", e.toString());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } else {
            LOG.warn("Invalid Garmin request: user agent: {}; client id: {}; summariesRequestDTO keys: {}", userAgent, StringUtils.anonymize(garminClientId), requestBody.keySet());
        }
        return ResponseEntity.ok().build();
    }
}
