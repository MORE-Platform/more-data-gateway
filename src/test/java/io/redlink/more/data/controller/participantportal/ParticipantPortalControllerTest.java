package io.redlink.more.data.controller.participantportal;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.redlink.more.data.api.participant.v1.model.StudyConsentDTO;
import io.redlink.more.data.configuration.SchedulerProperties;
import io.redlink.more.data.configuration.SecurityConfig;
import io.redlink.more.data.controller.GlobalControllerExceptionHandler;
import io.redlink.more.data.model.Contact;
import io.redlink.more.data.model.DataHealth;
import io.redlink.more.data.model.Observation;
import io.redlink.more.data.model.ObservationDataState;
import io.redlink.more.data.model.RoutingInfo;
import io.redlink.more.data.model.SimpleParticipant;
import io.redlink.more.data.model.Study;
import io.redlink.more.data.model.StudyParticipantUserDetails;
import io.redlink.more.data.model.scheduler.Duration;
import io.redlink.more.data.model.scheduler.Event;
import io.redlink.more.data.model.scheduler.RelativeDate;
import io.redlink.more.data.model.scheduler.RelativeEvent;
import io.redlink.more.data.model.scheduler.RelativeRecurrenceRule;
import io.redlink.more.data.service.ApplicationAccessService;
import io.redlink.more.data.service.DataHealthService;
import io.redlink.more.data.service.GatewayUserDetailService;
import io.redlink.more.data.service.LoginTokenService;
import io.redlink.more.data.service.ObservationExecutionService;
import io.redlink.more.data.service.RegistrationService;
import io.redlink.more.data.service.StudyService;
import io.redlink.more.data.service.milestone.ParticipantMilestoneService;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.result.MockMvcResultHandlers;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({ParticipantPortalController.class, GlobalControllerExceptionHandler.class})
@Import(SecurityConfig.class)
@AutoConfigureMockMvc
class ParticipantPortalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ApplicationAccessService applicationAccessService;

    @MockitoBean
    private StudyService studyService;

    @MockitoBean
    private LoginTokenService loginTokenService;

    @MockitoBean
    private RegistrationService registrationService;

    @MockitoBean
    private GatewayUserDetailService gatewayUserDetailService;

    @MockitoBean
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private DataHealthService dataHealthService;

    @MockitoBean
    private ObservationExecutionService observationExecutionService;

    @MockitoBean
    private ParticipantMilestoneService participantMilestoneService;

    @MockitoBean
    private SchedulerProperties schedulerProperties;

    @Test
    void testParticipantLoginSetsSession() throws Exception {
        Long studyId = 1L;
        String userDataRef = "user-ref";
        String loginCode = "login-code";
        String encodedCode = Base64.getEncoder().encodeToString(loginCode.getBytes(StandardCharsets.UTF_8));
        RoutingInfo routingInfo = new RoutingInfo(studyId, 1, OptionalInt.empty(), Set.of(), true, true);

        when(applicationAccessService.validateLogin(studyId, userDataRef, loginCode))
                .thenReturn(Optional.of(routingInfo));
        when(studyService.getStudyState(studyId)).thenReturn(Optional.of("active"));

        MvcResult result = mockMvc.perform(post("/participant-portal/api/v1/login/{studyId}/{userDataRef}", studyId, userDataRef)
                        .with(csrf())
                        .header("more-login-code", encodedCode))
                .andExpect(status().isNoContent())
                .andReturn();

        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertNotNull(session);
        assertNotNull(session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY));
    }

    @Test
    void testParticipantLoginInvalidBase64() throws Exception {
        Long studyId = 2L;
        String userDataRef = "user-ref";
        String invalidEncodedCode = "!!! not base64 !!!";

        mockMvc.perform(post("/participant-portal/api/v1/login/{studyId}/{userDataRef}", studyId, userDataRef)
                        .with(csrf())
                        .header("more-login-code", invalidEncodedCode))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testParticipantLoginInvalidCredentials() throws Exception {
        Long studyId = 3L;
        String userDataRef = "user-ref";
        String loginCode = "wrong-code";
        String encodedCode = Base64.getEncoder().encodeToString(loginCode.getBytes(StandardCharsets.UTF_8));

        when(applicationAccessService.validateLogin(studyId, userDataRef, loginCode))
                .thenReturn(Optional.empty());

        mockMvc.perform(post("/participant-portal/api/v1/login/{studyId}/{userDataRef}", studyId, userDataRef)
                        .with(csrf())
                        .header("more-login-code", encodedCode))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testParticipantLogout() throws Exception {
        long studyId = 4L;
        int participantId = 1;
        RoutingInfo routingInfo = new RoutingInfo(studyId, participantId, OptionalInt.empty(), Set.of(), true, true);
        StudyParticipantUserDetails userDetails = new StudyParticipantUserDetails(studyId, participantId, null);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, "some-context");

        mockMvc.perform(post("/participant-portal/api/v1/logout")
                        .with(user(userDetails))
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isNoContent());

        assertTrue(session.isInvalid());
    }

    @Test
    void testParticipantLogoutNoSession() throws Exception {
        long studyId = 5L;
        int participantId = 2;
        RoutingInfo routingInfo = new RoutingInfo(studyId, participantId, OptionalInt.empty(), Set.of(), true, true);
        StudyParticipantUserDetails userDetails = new StudyParticipantUserDetails(studyId, participantId, null);

        mockMvc.perform(post("/participant-portal/api/v1/logout")
                        .with(user(userDetails))
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void testParticipantLogoutUnauthenticated() throws Exception {
        mockMvc.perform(post("/participant-portal/api/v1/logout")
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testAcceptConsentSuccess() throws Exception {
        long studyId = 6L;
        int participantId = 3;
        RoutingInfo routingInfo = new RoutingInfo(studyId, participantId, OptionalInt.empty(), Set.of(), true, true);
        StudyParticipantUserDetails userDetails = new StudyParticipantUserDetails(studyId, participantId, null);

        StudyConsentDTO consentDTO = new StudyConsentDTO();
        consentDTO.setConsent(true);
        consentDTO.setDeviceId("MODEL#SERIAL");

        when(applicationAccessService.hasConsent(routingInfo)).thenReturn(false);
        when(studyService.getRoutingInfo(studyId, participantId)).thenReturn(Optional.of(routingInfo));
        when(studyService.getStudyState(studyId)).thenReturn(Optional.of("active"));

        mockMvc.perform(post("/participant-portal/api/v1/consent")
                        .with(csrf())
                        .with(user(userDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(consentDTO)))
                .andExpect(status().isNoContent());

        verify(applicationAccessService).validateAndStoreConsent(eq(routingInfo), any());
    }

    @Test
    void testAcceptConsentAlreadyReported() throws Exception {
        long studyId = 7L;
        int participantId = 4;
        RoutingInfo routingInfo = new RoutingInfo(studyId, participantId, OptionalInt.empty(), Set.of(), true, true);
        StudyParticipantUserDetails userDetails = new StudyParticipantUserDetails(studyId, participantId, null);

        when(applicationAccessService.hasConsent(routingInfo)).thenReturn(true);
        when(studyService.getRoutingInfo(studyId, participantId)).thenReturn(Optional.of(routingInfo));
        when(studyService.getStudyState(studyId)).thenReturn(Optional.of("active"));

        mockMvc.perform(post("/participant-portal/api/v1/consent")
                        .with(csrf())
                        .with(user(userDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict());
    }

    @Test
    void testRetrieveConsentDataSuccess() throws Exception {
        long studyId = 8L;
        int participantId = 5;
        RoutingInfo routingInfo = new RoutingInfo(studyId, participantId, OptionalInt.empty(), Set.of(), true, true);
        StudyParticipantUserDetails userDetails = new StudyParticipantUserDetails(studyId, participantId, null);
        Study study = new Study(studyId, "Title", true, "Info", "Finish", "active", "Consent",
                new Contact("Inst", "Person", "email", "phone"),
                LocalDate.now(), LocalDate.now(), LocalDate.now().plusDays(10),
                Collections.emptyList(), Instant.now(), Instant.now(),
                new SimpleParticipant(participantId, "alias", Instant.now(), Instant.now().plus(10, ChronoUnit.DAYS)));

        when(studyService.getRoutingInfo(studyId, participantId)).thenReturn(Optional.of(routingInfo));
        when(applicationAccessService.hasConsent(routingInfo)).thenReturn(false);
        when(studyService.getStudyState(studyId)).thenReturn(Optional.of("active"));
        when(studyService.getStudy(routingInfo)).thenReturn(Optional.of(Pair.of(study, Collections.emptyList())));

        mockMvc.perform(get("/participant-portal/api/v1/consent")
                        .with(user(userDetails)))
                .andExpect(status().isOk());
    }

    @Test
    void testRetrieveConsentDataUnauthorized() throws Exception {
        long studyId = 9L;
        int participantId = 6;
        RoutingInfo routingInfo = new RoutingInfo(studyId, participantId, OptionalInt.empty(), Set.of(), true, true);
        StudyParticipantUserDetails userDetails = new StudyParticipantUserDetails(studyId, participantId, null);

        when(studyService.getRoutingInfo(studyId, participantId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/participant-portal/api/v1/consent")
                        .with(user(userDetails)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testRetrieveConsentDataAlreadyReported() throws Exception {
        long studyId = 10L;
        int participantId = 7;
        RoutingInfo routingInfo = new RoutingInfo(studyId, participantId, OptionalInt.empty(), Set.of(), true, true);
        StudyParticipantUserDetails userDetails = new StudyParticipantUserDetails(studyId, participantId, null);

        when(studyService.getRoutingInfo(studyId, participantId)).thenReturn(Optional.of(routingInfo));
        when(studyService.getStudyState(studyId)).thenReturn(Optional.of("active"));
        when(applicationAccessService.hasConsent(routingInfo)).thenReturn(true);

        mockMvc.perform(get("/participant-portal/api/v1/consent")
                        .with(user(userDetails)))
                .andExpect(status().isConflict());
    }

    @Test
    void testRetrieveConsentDataStudyNotFound() throws Exception {
        long studyId = 11L;
        int participantId = 8;
        RoutingInfo routingInfo = new RoutingInfo(studyId, participantId, OptionalInt.empty(), Set.of(), true, true);
        StudyParticipantUserDetails userDetails = new StudyParticipantUserDetails(studyId, participantId, null);

        when(studyService.getRoutingInfo(studyId, participantId)).thenReturn(Optional.of(routingInfo));
        when(applicationAccessService.hasConsent(routingInfo)).thenReturn(false);
        when(studyService.getStudyState(studyId)).thenReturn(Optional.of("active"));
        when(studyService.getStudy(routingInfo)).thenReturn(Optional.empty());

        mockMvc.perform(get("/participant-portal/api/v1/consent")
                        .with(user(userDetails)))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    void testAcceptConsentWithMockUserFails() throws Exception {
        // This should fail because the principal is not RoutingInfoUserDetails
        // It's now handled by the ExceptionHandler and returns 500
        mockMvc.perform(post("/participant-portal/api/v1/consent")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testGetStudyConfigurationSuccess() throws Exception {
        Instant now = Instant.now().truncatedTo(ChronoUnit.HOURS);
        long studyId = 12L;
        var studyStart = LocalDate.now().minusDays(1);
        var studyEnd = LocalDate.now().plusDays(14);


        int participantId = 9;
        Instant participantStart = now.minus(26, ChronoUnit.HOURS);
        Instant participantEnd = now.plus(10, ChronoUnit.DAYS);

        Instant absStart = now.minus(1, ChronoUnit.HOURS);
        Instant absEnd = now.plus(3, ChronoUnit.HOURS);

        ZoneId zone = ZoneId.systemDefault();

        RoutingInfo routingInfo = new RoutingInfo(studyId, participantId, OptionalInt.empty(), Set.of(), true, true);
        StudyParticipantUserDetails userDetails = new StudyParticipantUserDetails(studyId, participantId, null);
        var observation1 = new Observation(1, null, "observation 1", "type", "participant info",
                null,
                new Event().setDateStart(absStart).setDateEnd(absEnd),
                null, null, //created, modified
                false, false, false, Set.of());
        var observation2 = new Observation(2, null, "observation 2", "type", "participant info",
                null,
                new RelativeEvent()
                        .setDtstart(new RelativeDate()
                                .setTime(LocalTime.from(absStart.atZone(zone)))
                                .setOffset(new Duration().setValue(1).setUnit(Duration.Unit.DAY)))
                        .setDtend(new RelativeDate()
                                .setTime(LocalTime.from(absEnd.atZone(zone)))
                                .setOffset(new Duration().setValue(1).setUnit(Duration.Unit.DAY)))
                        .setRrrule(new RelativeRecurrenceRule()
                                .setFrequency(new Duration().setValue(1).setUnit(Duration.Unit.DAY))
                                .setEndAfter(new Duration().setValue(8).setUnit(Duration.Unit.DAY))),
                now.minus(1, ChronoUnit.DAYS),
                now.minus(1, ChronoUnit.DAYS),
                false, false, false, Set.of());
        Study study = new Study(studyId, "Title", true, "Info", "Finish", "active", "Consent",
                new Contact("Inst", "Person", "email", "phone"),
                studyStart, studyStart, studyEnd,
                List.of(observation1, observation2), Instant.now(), Instant.now(),
                new SimpleParticipant(participantId, "alias", participantStart, participantEnd));

        when(studyService.getRoutingInfo(studyId, participantId)).thenReturn(Optional.of(routingInfo));
        when(studyService.getStudyState(studyId)).thenReturn(Optional.of("active"));
        when(applicationAccessService.hasConsent(routingInfo)).thenReturn(true);
        when(studyService.getStudy(routingInfo)).thenReturn(Optional.of(Pair.of(study, Collections.emptyList())));
        when(dataHealthService.checkDataHealth(studyId, participantId, observation1.observationId(), absStart)).thenReturn(
                new DataHealth(true, ObservationDataState.COMPLETE)
        );
        when(dataHealthService.checkDataHealth(studyId, participantId, observation2.observationId(), absStart)).thenReturn(
                new DataHealth(false, ObservationDataState.COMPLETE)
        );
        //when(dataHealthService.checkDataHealth(eq(studyId), eq(participantId), anyInt(), any())).thenReturn(
        //        new DataHealth(true, ObservationDataState.MISSING)
        //);

        mockMvc.perform(get("/participant-portal/api/v1/config/study")
                        .with(user(userDetails)))
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.studyState").value("active"))

                .andExpect(jsonPath("$.participant.id").doesNotExist())
                .andExpect(jsonPath("$.participant.alias").value("alias"))

                .andExpect(jsonPath("$.studyTitle").value("Title"))
                .andExpect(jsonPath("$.participantInfo").value("Info"))
                .andExpect(jsonPath("$.consentInfo").value("Consent"))
                .andExpect(jsonPath("$.finishText").value("Finish"))

                .andExpect(jsonPath("$.contact.institute").value("Inst"))
                .andExpect(jsonPath("$.contact.person").value("Person"))
                .andExpect(jsonPath("$.contact.email").value("email"))
                .andExpect(jsonPath("$.contact.phoneNumber").value("phone"))

                // Study dates - using variables from the test
                .andExpect(jsonPath("$.start").value(studyStart.toString()))
                .andExpect(jsonPath("$.end").value(studyEnd.toString()))

                // === Observation 1 ===
                .andExpect(jsonPath("$.observations[0].observationId").value("1"))
                .andExpect(jsonPath("$.observations[0].observationType").value("type"))
                .andExpect(jsonPath("$.observations[0].observationTitle").value("observation 1"))
                .andExpect(jsonPath("$.observations[0].participantInfo").value("participant info"))
                .andExpect(jsonPath("$.observations[0].required").value(true))
                .andExpect(jsonPath("$.observations[0].hidden").value(false))
                .andExpect(jsonPath("$.observations[0].noSchedule").value(false))
                .andExpect(jsonPath("$.observations[0].reminder").value(false))
                .andExpect(jsonPath("$.observations[0].version").doesNotExist())
                .andExpect(jsonPath("$.observations[0].configuration").doesNotExist())

                // Schedule for observation 1 (single absolute event)
                .andExpect(jsonPath("$.observations[0].schedule[0].start").value(absStart.toString()))
                .andExpect(jsonPath("$.observations[0].schedule[0].end").value(absEnd.toString()))
                .andExpect(jsonPath("$.observations[0].schedule[0].dataHealth").value("completed"))

                // === Observation 2 ===
                .andExpect(jsonPath("$.observations[1].observationId").value("2"))
                .andExpect(jsonPath("$.observations[1].observationType").value("type"))
                .andExpect(jsonPath("$.observations[1].observationTitle").value("observation 2"))
                .andExpect(jsonPath("$.observations[1].participantInfo").value("participant info"))
                .andExpect(jsonPath("$.observations[1].required").value(true))
                .andExpect(jsonPath("$.observations[1].hidden").value(false))
                .andExpect(jsonPath("$.observations[1].noSchedule").value(false))
                .andExpect(jsonPath("$.observations[1].reminder").value(false))
                .andExpect(jsonPath("$.observations[1].version").isNumber())
                .andExpect(jsonPath("$.observations[1].configuration").doesNotExist())

                // Schedule entries for observation 2
                .andExpect(jsonPath("$.observations[1].schedule.length()").value(8))

                // First schedule entry (should be completed according to the mocked dataHealth)
                .andExpect(jsonPath("$.observations[1].schedule[0].start").value(absStart.minus(1, ChronoUnit.DAYS).toString()))
                .andExpect(jsonPath("$.observations[1].schedule[0].end").value(absEnd.minus(1, ChronoUnit.DAYS).toString()))
                .andExpect(jsonPath("$.observations[1].schedule[0].dataHealth").doesNotExist())

                // The rest of the recurring entries (dataHealth is null or "invalid" as in the example JSON)
                .andExpect(jsonPath("$.observations[1].schedule[1].start").value(absStart.toString()))
                .andExpect(jsonPath("$.observations[1].schedule[1].end").value(absEnd.toString()))
                .andExpect(jsonPath("$.observations[1].schedule[1].dataHealth").value("invalid"))
                .andExpect(jsonPath("$.observations[1].schedule[2].start").value(absStart.plus(1, ChronoUnit.DAYS).toString()))
                .andExpect(jsonPath("$.observations[1].schedule[2].end").value(absEnd.plus(1, ChronoUnit.DAYS).toString()))
                .andExpect(jsonPath("$.observations[1].schedule[2].dataHealth").doesNotExist())
                .andExpect(jsonPath("$.observations[1].schedule[3].start").value(absStart.plus(2, ChronoUnit.DAYS).toString()))
                .andExpect(jsonPath("$.observations[1].schedule[3].end").value(absEnd.plus(2, ChronoUnit.DAYS).toString()))
                .andExpect(jsonPath("$.observations[1].schedule[3].dataHealth").doesNotExist())
                .andExpect(jsonPath("$.observations[1].schedule[4].start").value(absStart.plus(3, ChronoUnit.DAYS).toString()))
                .andExpect(jsonPath("$.observations[1].schedule[4].end").value(absEnd.plus(3, ChronoUnit.DAYS).toString()))
                .andExpect(jsonPath("$.observations[1].schedule[4].dataHealth").doesNotExist())
                .andExpect(jsonPath("$.observations[1].schedule[5].start").value(absStart.plus(4, ChronoUnit.DAYS).toString()))
                .andExpect(jsonPath("$.observations[1].schedule[5].end").value(absEnd.plus(4, ChronoUnit.DAYS).toString()))
                .andExpect(jsonPath("$.observations[1].schedule[5].dataHealth").doesNotExist())
                .andExpect(jsonPath("$.observations[1].schedule[6].start").value(absStart.plus(5, ChronoUnit.DAYS).toString()))
                .andExpect(jsonPath("$.observations[1].schedule[6].end").value(absEnd.plus(5, ChronoUnit.DAYS).toString()))
                .andExpect(jsonPath("$.observations[1].schedule[6].dataHealth").doesNotExist())
                .andExpect(jsonPath("$.observations[1].schedule[7].start").value(absStart.plus(6, ChronoUnit.DAYS).toString()))
                .andExpect(jsonPath("$.observations[1].schedule[7].end").value(absEnd.plus(6, ChronoUnit.DAYS).toString()))
                .andExpect(jsonPath("$.observations[1].schedule[7].dataHealth").doesNotExist());
    }

    @Test
    void testGetStudyConfigurationUnauthorized() throws Exception {
        long studyId = 13L;
        int participantId = 10;
        RoutingInfo routingInfo = new RoutingInfo(studyId, participantId, OptionalInt.empty(), Set.of(), true, true);
        StudyParticipantUserDetails userDetails = new StudyParticipantUserDetails(studyId, participantId, null);

        when(studyService.getRoutingInfo(studyId, participantId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/participant-portal/api/v1/config/study")
                        .with(user(userDetails)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testGetStudyConfigurationNotFound() throws Exception {
        long studyId = 14L;
        int participantId = 11;
        RoutingInfo routingInfo = new RoutingInfo(studyId, participantId, OptionalInt.empty(), Set.of(), true, true);
        StudyParticipantUserDetails userDetails = new StudyParticipantUserDetails(studyId, participantId, null);

        when(studyService.getRoutingInfo(studyId, participantId)).thenReturn(Optional.of(routingInfo));
        when(studyService.getStudyState(studyId)).thenReturn(Optional.of("active"));
        when(applicationAccessService.hasConsent(routingInfo)).thenReturn(true);
        when(studyService.getStudy(routingInfo)).thenReturn(Optional.empty());

        mockMvc.perform(get("/participant-portal/api/v1/config/study")
                        .with(user(userDetails)))
                .andExpect(status().isNotFound());
    }

    @Test
    void testGetStudyConfigurationNoConsent() throws Exception {
        long studyId = 15L;
        int participantId = 12;
        RoutingInfo routingInfo = new RoutingInfo(studyId, participantId, OptionalInt.empty(), Set.of(), true, true);
        StudyParticipantUserDetails userDetails = new StudyParticipantUserDetails(studyId, participantId, null);

        when(studyService.getRoutingInfo(studyId, participantId)).thenReturn(Optional.of(routingInfo));
        when(studyService.getStudyState(studyId)).thenReturn(Optional.of("active"));
        when(applicationAccessService.hasConsent(routingInfo)).thenReturn(false);

        mockMvc.perform(get("/participant-portal/api/v1/config/study")
                        .with(user(userDetails)))
                .andExpect(status().isForbidden());
    }

}
