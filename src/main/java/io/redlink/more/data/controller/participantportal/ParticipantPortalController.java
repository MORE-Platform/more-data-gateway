package io.redlink.more.data.controller.participantportal;

import io.redlink.more.data.api.participant.v1.model.StudyConsentDTO;
import io.redlink.more.data.api.participant.v1.model.StudyDTO;
import io.redlink.more.data.api.participant.v1.webservices.AuthorizationApi;
import io.redlink.more.data.api.participant.v1.webservices.ConfigurationApi;
import io.redlink.more.data.controller.transformer.ParticipantPortalTransformer;
import io.redlink.more.data.exception.NotAuthorizedException;
import io.redlink.more.data.model.CompletedData;
import io.redlink.more.data.model.DataHealth;
import io.redlink.more.data.model.ObservationDataState;
import io.redlink.more.data.model.ParticipantConsent;
import io.redlink.more.data.model.RoutingInfo;
import io.redlink.more.data.model.StudyParticipantUserDetails;
import io.redlink.more.data.service.ApplicationAccessService;
import io.redlink.more.data.service.DataHealthService;
import io.redlink.more.data.service.ObservationExecutionService;
import io.redlink.more.data.service.StudyService;
import io.redlink.more.data.util.ParticipantUtils;
import io.redlink.more.data.util.StringUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Controller
@RestController
@RequestMapping(value = "/participant-portal/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
public class ParticipantPortalController implements AuthorizationApi, ConfigurationApi {
    private static final Logger LOG = LoggerFactory.getLogger(ParticipantPortalController.class);

    private static final Collection<String> ALLOWED_STUDY_STATES = Set.of("active", "paused", "preview", "paused-preview");

    private final ApplicationAccessService applicationAccessService;
    private final StudyService studyService;
    private final DataHealthService dataHealthService;
    private final ObservationExecutionService observationExecutionService;

    ParticipantPortalController(
            ApplicationAccessService applicationAccessService,
            StudyService studyService,
            DataHealthService dataHealthService,
            ObservationExecutionService observationExecutionService) {
        this.applicationAccessService = applicationAccessService;
        this.studyService = studyService;
        this.dataHealthService = dataHealthService;
        this.observationExecutionService = observationExecutionService;
    }

    @Override
    public ResponseEntity<Void> participantLogin(Long moreStudyId, String moreUserDataReference, String moreLoginCode) {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        final HttpServletRequest request;
        if (requestAttributes instanceof ServletRequestAttributes) {
            request = ((ServletRequestAttributes) requestAttributes).getRequest();
        } else {
            throw new IllegalStateException("Request is not a ServletRequest");
        }

        final String decodedToken;
        try {
            decodedToken = StringUtils.base64Decode(moreLoginCode);
        } catch (RuntimeException e) {
            LOG.warn("Could not decode more login code", e);
            throw new NotAuthorizedException("Login Code is not valid");
        }

        RoutingInfo routingInfo = applicationAccessService.validateLogin(moreStudyId, moreUserDataReference, decodedToken)
                .filter(ri -> validateStudyState(ri.studyId()))
                .orElseThrow(NotAuthorizedException::new);
        StudyParticipantUserDetails userDetails = new StudyParticipantUserDetails(
                routingInfo.studyId(), routingInfo.participantId(),
                null);
        Authentication auth = new UsernamePasswordAuthenticationToken(
                userDetails,
                null,
                userDetails.getAuthorities()
        );
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);

        HttpSession session = request.getSession(true);
        session.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                context
        );
        // change session id to prevent session fixation
        request.changeSessionId();
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> participantLogout() {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (!(requestAttributes instanceof ServletRequestAttributes servletRequestAttributes)) {
            throw new IllegalStateException("Request is not a ServletRequest");
        }

        HttpServletRequest request = servletRequestAttributes.getRequest();
        HttpSession session = request.getSession(false);

        if (session != null) {
            session.removeAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
            session.invalidate();
        }

        SecurityContextHolder.clearContext();

        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> acceptConsent(StudyConsentDTO studyConsentDTO) {
        RoutingInfo routingInfo = validateRoutingInfo()
                .orElseThrow(NotAuthorizedException::new);
        if (applicationAccessService.hasConsent(routingInfo)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
        final ParticipantConsent consent = ParticipantUtils.convert(studyConsentDTO);
        applicationAccessService.validateAndStoreConsent(routingInfo, consent);
        return ResponseEntity.noContent().build();
    }


    @Override
    public ResponseEntity<StudyDTO> retrieveConsentData() {
        RoutingInfo routingInfo = validateRoutingInfo()
                .orElseThrow(NotAuthorizedException::new);
        if (applicationAccessService.hasConsent(routingInfo)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
        var studyData = studyService.getStudy(routingInfo);
        return studyData
                .map(studyListPair ->
                        ResponseEntity.ok(ParticipantPortalTransformer.toDTO(studyListPair.getLeft(), studyListPair.getRight())))
                .orElseGet(() ->
                        ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    @Override
    public ResponseEntity<StudyDTO> getStudyConfiguration() {
        RoutingInfo routingInfo = validateRoutingInfo()
                .orElseThrow(NotAuthorizedException::new);

        var studyData = studyService.getStudy(routingInfo)
                .filter(sd -> sd.getLeft().active() || "paused".equals(sd.getLeft().studyState()));
        if (studyData.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        var studyDTO = ParticipantPortalTransformer.toDTO(studyData.get().getLeft(), studyData.get().getRight());

        List<CompletedData> completedData = observationExecutionService.getCompletedData(routingInfo);
        //add the dataHealth information to the studyDTO (Not done in Transformer as this need calls to the dataHealthService)
        studyDTO.getObservations().forEach(observation -> {
            var observationNonMissingData = completedData.stream().filter(d -> Objects.equals(d.observationId(), observation.getObservationId())).toList();
            observation.getSchedule().forEach(observationSchedule -> {
                DataHealth dataHealth;
                if (!observationNonMissingData.isEmpty()
                        && observationSchedule.getStart() != null
                        && observationSchedule.getEnd() != null
                        && observationNonMissingData.stream().anyMatch(nonMissingData1 ->
                        !observationSchedule.getStart().isBefore(nonMissingData1.scheduleStart())
                                && !observationSchedule.getEnd().isAfter(nonMissingData1.scheduleEnd()))) {
                    dataHealth = new DataHealth(true, ObservationDataState.COMPLETE);
                } else if (observationSchedule.getStart() != null
                        && observationSchedule.getStart().isBefore(Instant.now())) {
                    dataHealth = dataHealthService.checkDataHealth(
                            routingInfo.studyId(),
                            routingInfo.participantId(),
                            Integer.parseInt(observation.getObservationId()), //ObservationId in the API is a String :(
                            observationSchedule.getStart());
                    LOG.debug(
                            "check dataHealth[study:{}, participant:{}, observation:{}, start:{}] -> {}",
                            routingInfo.studyId(),
                            routingInfo.participantId(),
                            observation.getObservationId(),
                            observationSchedule.getStart(),
                            dataHealth);
                } else {
                    dataHealth = null; //no need to check for existing data if the observation has not yet started
                }
                observationSchedule.setDataHealth(ParticipantPortalTransformer.toDataHealStateDto(dataHealth));
            });
        });

        return ResponseEntity.ok(studyDTO);
    }


    private Optional<RoutingInfo> validateRoutingInfo() {
        var studyParticipant = StudyParticipantUserDetails.getCurrent().getStudyParticipantReference();
        return studyService.getRoutingInfo(studyParticipant.studyId(), studyParticipant.participantId())
                .filter(routingInfo -> validateStudyState(routingInfo.studyId()));
    }

    private boolean validateStudyState(long studyId) {
        return studyService.getStudyState(studyId)
                .filter(ALLOWED_STUDY_STATES::contains)
                .isPresent();
    }
}
