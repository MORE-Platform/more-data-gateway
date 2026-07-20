package io.redlink.more.data.controller;

import io.redlink.more.data.api.execution.v1.webservices.ExecutionApi;
import io.redlink.more.data.configuration.AuthenticationFacade;
import io.redlink.more.data.exception.BadRequestException;
import io.redlink.more.data.exception.ForbiddenException;
import io.redlink.more.data.exception.NotAuthorizedException;
import io.redlink.more.data.exception.NotFoundException;
import io.redlink.more.data.exception.RegistrationNotPossibleException;
import io.redlink.more.data.model.GatewayUserDetails;
import io.redlink.more.data.model.RoutingInfo;
import io.redlink.more.data.model.StudyParticipantUserDetails;
import io.redlink.more.data.service.ObservationExecutionService;
import io.redlink.more.data.service.StudyService;
import io.redlink.more.data.util.DateTimeUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping(value = "/api/v1/execution", produces = MediaType.APPLICATION_JSON_VALUE)
public class ObservationExecutionController implements ExecutionApi {
    private static final Logger LOG = LoggerFactory.getLogger(ObservationExecutionController.class);
    private final AuthenticationFacade authenticationFacade;
    private final StudyService studyService;
    private final ObservationExecutionService observationExecutionService;

    public ObservationExecutionController(AuthenticationFacade authenticationFacade, StudyService studyService, ObservationExecutionService observationExecutionService) {
        this.authenticationFacade = authenticationFacade;
        this.studyService = studyService;
        this.observationExecutionService = observationExecutionService;
    }

    @Override
    public ResponseEntity<Void> execObservation(String observationId, String scheduleStart, String scheduleEnd, String redirect) {
        try {
            final Instant start = DateTimeUtils.parseInstant(scheduleStart);
            final Instant end = DateTimeUtils.parseInstant(scheduleEnd);
            final RoutingInfo routingInfo = getRoutingInfo()
                    .orElseThrow(() -> new AccessDeniedException("No credentials found!"));

            var url = observationExecutionService.executeObservation(observationId, start, end, routingInfo, redirect);

            if (url.isEmpty()) {
                url = Optional.of(UriComponentsBuilder.fromUriString(fallbackTarget())
                        .replaceQueryParam("status", HttpStatus.CONFLICT.value())
                        .build().toUri());
            }
            return ResponseEntity.status(HttpStatus.FOUND).location(url.get()).build();
        } catch (RuntimeException e) {
            LOG.error("Error executing observation {}: {}", observationId, e.getMessage(), e);
            String target = (redirect != null && !redirect.isBlank()) ? redirect : fallbackTarget();
            URI redirectUrl = UriComponentsBuilder.fromUriString(target)
                    .queryParam("status", mapToStatus(e))
                    .build().toUri();
            return ResponseEntity.status(HttpStatus.FOUND).location(redirectUrl).build();
        }
    }


    @Override
    public ResponseEntity<String> callback() {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
        Map<String, String> params = new HashMap<>();
        request.getParameterMap().forEach((key, value) -> params.put(key, value[0]));

        Map<String, String> pathVars = (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (pathVars != null) {
            params.putAll(pathVars);
        }

        UriComponentsBuilder builder = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .path("/end.htm");
        params.forEach((k, v) -> {
            if (k.equalsIgnoreCase("savedId")) {
                builder.replaceQueryParam("savedid", v);
            } else {
                builder.replaceQueryParam(k, v);
            }
        });

        URI redirectUrl = builder.build().toUri();

        LOG.info("process callback: pathVars: {}, params: {}", pathVars, params);

        int status = HttpStatus.OK.value();

        try {
            var redirect = observationExecutionService.processCallback(params);

            if (redirect.isPresent()) {
                UriComponentsBuilder redirectBuilder = UriComponentsBuilder.fromUri(redirect.get());
                params.forEach((k, v) -> {
                    if (k.equalsIgnoreCase("savedId")) {
                        redirectBuilder.replaceQueryParam("savedid", v);
                    } else {
                        redirectBuilder.replaceQueryParam(k, v);
                    }
                });
                redirectUrl = redirectBuilder.build().toUri();
            }
        } catch (RuntimeException e) {
            LOG.error("Error processing callback: {}", e.getMessage(), e);
            status = mapToStatus(e);
        }

        redirectUrl = UriComponentsBuilder.fromUri(redirectUrl)
                .replaceQueryParam("status", status)
                .build().toUri();

        LOG.info("Redirecting participant {} to url `{}`", getRoutingInfo().orElse(null), redirectUrl);
        return ResponseEntity.status(HttpStatus.FOUND).location(redirectUrl).build();
    }

    @Override
    public ResponseEntity<String> callbackEndHtm() {
        String html = "<html><body><h1>Completed! Thank you!</h1></body></html>";
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(html);
    }


    private Optional<RoutingInfo> getRoutingInfo() {
        Authentication authentication = authenticationFacade.getAuthentication();
        if (authentication == null) {
            LOG.warn("No authentication found!");
            return Optional.empty();
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof GatewayUserDetails userDetails) {
            return Optional.ofNullable(userDetails.getRoutingInfo());
        } else if (principal instanceof StudyParticipantUserDetails participantDetails) {
            var ref = participantDetails.getStudyParticipantReference();
            return studyService.getRoutingInfo(ref.studyId(), ref.participantId());
        }
        return Optional.empty();
    }

    private String fallbackTarget() {
        return ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/execution/callback/end.htm")
                .toUriString();
    }

    private int mapToStatus(Exception e) {
        if (e instanceof ForbiddenException || e instanceof AccessDeniedException || e instanceof IllegalArgumentException) {
            return HttpStatus.FORBIDDEN.value();
        } else if (e instanceof NotFoundException) {
            return HttpStatus.NOT_FOUND.value();
        } else if (e instanceof NotAuthorizedException) {
            return HttpStatus.UNAUTHORIZED.value();
        } else if (e instanceof BadRequestException) {
            return HttpStatus.BAD_REQUEST.value();
        } else if (e instanceof RegistrationNotPossibleException) {
            return HttpStatus.CONFLICT.value();
        }
        return HttpStatus.INTERNAL_SERVER_ERROR.value();
    }
}
