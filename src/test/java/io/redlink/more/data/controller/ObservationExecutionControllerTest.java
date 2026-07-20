package io.redlink.more.data.controller;

import io.redlink.more.data.configuration.AuthenticationFacade;
import io.redlink.more.data.model.ActiveObservation;
import io.redlink.more.data.model.CompletedData;
import io.redlink.more.data.model.GatewayUserDetails;
import io.redlink.more.data.model.RoutingInfo;
import io.redlink.more.data.service.ObservationExecutionService;
import io.redlink.more.data.service.StudyService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ObservationExecutionControllerTest {

    @Mock
    private AuthenticationFacade authenticationFacade;

    @Mock
    private StudyService studyService;

    @Mock
    private ObservationExecutionService observationExecutionService;

    @Mock
    private HttpServletRequest request;

    private ObservationExecutionController observationExecutionController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        observationExecutionController = new ObservationExecutionController(authenticationFacade, studyService, observationExecutionService);
        mockMvc = MockMvcBuilders.standaloneSetup(observationExecutionController)
                .setControllerAdvice(new GlobalControllerExceptionHandler())
                .build();
        ServletRequestAttributes attributes = new ServletRequestAttributes(request);
        RequestContextHolder.setRequestAttributes(attributes);
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void testExecObservationSuccess() {
        String observationId = "1";
        Instant start = Instant.now();
        Instant end = start.plusSeconds(3600);
        String redirect = "http://redirect.com";
        RoutingInfo routingInfo = new RoutingInfo(1L, 1, OptionalInt.empty(), Set.of(), true, true);
        GatewayUserDetails userDetails = new GatewayUserDetails("user", "pass", Set.of(), routingInfo);
        Authentication authentication = mock(Authentication.class);

        when(authenticationFacade.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(observationExecutionService.executeObservation(observationId, start, end, routingInfo, redirect)).thenReturn(Optional.of(URI.create("http://limesurvey.com")));

        ResponseEntity<Void> response = observationExecutionController.execObservation(observationId, start.toString(), end.toString(), redirect);

        assertEquals(HttpStatus.FOUND, response.getStatusCode());
        assertEquals("http://limesurvey.com", response.getHeaders().getLocation().toString());
    }

    @Test
    void testExecObservationAlreadyReportedRedirect() {
        String observationId = "1";
        Instant start = Instant.now();
        Instant end = start.plusSeconds(3600);
        RoutingInfo routingInfo = new RoutingInfo(1L, 1, OptionalInt.empty(), Set.of(), true, true);
        GatewayUserDetails userDetails = new GatewayUserDetails("user", "pass", Set.of(), routingInfo);
        Authentication authentication = mock(Authentication.class);
        ActiveObservation activeObservation = new ActiveObservation(observationId, start, end);
        CompletedData completedData = CompletedData.fromActiveObservation(activeObservation);

        when(authenticationFacade.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(userDetails);

        when(observationExecutionService.executeObservation(eq(observationId), any(), any(), eq(routingInfo), any()))
                .thenReturn(Optional.of(URI.create("http://redirect.com?status=200")));

        ResponseEntity<Void> response = observationExecutionController.execObservation(observationId, start.toString(), end.toString(), null);

        assertEquals(HttpStatus.FOUND, response.getStatusCode());
        assertEquals("http://redirect.com?status=200", response.getHeaders().getLocation().toString());
    }

    @Test
    void testExecObservationAlreadyReportedDefaultRedirect() {
        String observationId = "1";
        Instant start = Instant.now();
        Instant end = start.plusSeconds(3600);
        RoutingInfo routingInfo = new RoutingInfo(1L, 1, OptionalInt.empty(), Set.of(), true, true);
        GatewayUserDetails userDetails = new GatewayUserDetails("user", "pass", Set.of(), routingInfo);
        Authentication authentication = mock(Authentication.class);
        ActiveObservation activeObservation = new ActiveObservation(observationId, start, end);
        CompletedData completedData = CompletedData.fromActiveObservation(activeObservation);

        when(authenticationFacade.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(userDetails);

        when(observationExecutionService.executeObservation(eq(observationId), any(), any(), eq(routingInfo), any()))
                .thenReturn(Optional.empty());
        when(request.getContextPath()).thenReturn("");

        ResponseEntity<Void> response = observationExecutionController.execObservation(observationId, start.toString(), end.toString(), null);

        assertEquals(HttpStatus.FOUND, response.getStatusCode());
        String location = response.getHeaders().getLocation().toString();
        assertTrue(location.endsWith("/api/v1/execution/callback/end.htm?status=409"));
    }

    @Test
    void testExecObservationDateOnly() throws Exception {
        String observationId = "1";
        String dateOnly = "2026-04-14";
        RoutingInfo routingInfo = new RoutingInfo(1L, 1, OptionalInt.empty(), Set.of(), true, true);
        GatewayUserDetails userDetails = new GatewayUserDetails("user", "pass", Set.of(), routingInfo);
        Authentication authentication = mock(Authentication.class);

        when(authenticationFacade.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(observationExecutionService.executeObservation(eq(observationId), any(), any(), eq(routingInfo), any())).thenReturn(Optional.of(URI.create("http://limesurvey.com")));

        org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder requestBuilder = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/execution/observation/{observation-id}/", observationId)
                .param("schedule-start", dateOnly)
                .param("schedule-end", dateOnly);

        mockMvc.perform(requestBuilder)
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().is3xxRedirection());
    }

    @Test
    void testExecObservationInvalidDate() throws Exception {
        String observationId = "1";
        String invalidDate = "not-a-date";

        org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder requestBuilder = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/execution/observation/{observation-id}/", observationId)
                .param("schedule-start", invalidDate)
                .param("schedule-end", invalidDate);

        mockMvc.perform(requestBuilder)
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().is3xxRedirection())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern("**/end.htm?status=403"));
    }

    @Test
    void testExecObservationForbidden() throws Exception {
        String observationId = "1";
        Instant start = Instant.now();
        Instant end = start.plusSeconds(3600);
        RoutingInfo routingInfo = new RoutingInfo(1L, 1, OptionalInt.empty(), Set.of(), true, true);
        GatewayUserDetails userDetails = new GatewayUserDetails("user", "pass", Set.of(), routingInfo);
        Authentication authentication = mock(Authentication.class);

        when(authenticationFacade.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(observationExecutionService.executeObservation(eq(observationId), any(), any(), eq(routingInfo), any())).thenThrow(new io.redlink.more.data.exception.ForbiddenException("Forbidden test"));

        org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder requestBuilder = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/execution/observation/{observation-id}/", observationId)
                .param("schedule-start", start.toString())
                .param("schedule-end", end.toString());

        mockMvc.perform(requestBuilder)
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().is3xxRedirection())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern("**/end.htm?status=403"));
    }

    @Test
    void testCallbackIllegalArgument() throws Exception {
        RoutingInfo routingInfo = new RoutingInfo(1L, 1, OptionalInt.empty(), Set.of(), true, true);
        GatewayUserDetails userDetails = new GatewayUserDetails("user", "pass", Set.of(), routingInfo);
        Authentication authentication = mock(Authentication.class);
        ActiveObservation activeObservation = new ActiveObservation("1", Instant.now(), Instant.now().plusSeconds(3600));

        when(authenticationFacade.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(userDetails);

        when(observationExecutionService.processCallback(any())).thenThrow(new IllegalArgumentException("Missing params"));

        org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder requestBuilder = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/execution/callback");

        mockMvc.perform(requestBuilder)
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().is3xxRedirection())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern("**/end.htm?status=403"));
    }

    @Test
    void testCallbackProcessFailure() throws Exception {
        RoutingInfo routingInfo = new RoutingInfo(1L, 1, OptionalInt.empty(), Set.of(), true, true);
        GatewayUserDetails userDetails = new GatewayUserDetails("user", "pass", Set.of(), routingInfo);
        Authentication authentication = mock(Authentication.class);
        ActiveObservation activeObservation = new ActiveObservation("1", Instant.now(), Instant.now().plusSeconds(3600));

        when(authenticationFacade.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(userDetails);

        when(observationExecutionService.processCallback(any())).thenReturn(Optional.empty());

        org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder requestBuilder = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/execution/callback")
                .requestAttr(org.springframework.web.servlet.HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, new HashMap<>());

        mockMvc.perform(requestBuilder)
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().is3xxRedirection())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern("**/end.htm?status=200"));
    }

    @Test
    void testCallbackRedirectsToEndHtm() {
        RoutingInfo routingInfo = new RoutingInfo(1L, 1, OptionalInt.empty(), Set.of(), true, true);
        GatewayUserDetails userDetails = new GatewayUserDetails("user", "pass", Set.of(), routingInfo);
        Authentication authentication = mock(Authentication.class);

        when(authenticationFacade.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(userDetails);
        lenient().when(request.getParameterMap()).thenReturn(new HashMap<>(Map.of(
                "savedId", new String[]{"123"},
                "observationid", new String[]{"1"}
        )));
        lenient().when(request.getRequestURI()).thenReturn("/api/v1/execution/callback");
        lenient().when(request.getRequestURL()).thenReturn(new StringBuffer("http://localhost:8080/api/v1/execution/callback"));
        lenient().when(request.getContextPath()).thenReturn("");

        lenient().when(request.getAttribute(org.springframework.web.servlet.HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE)).thenReturn(new HashMap<>());
        lenient().when(observationExecutionService.processCallback(any())).thenReturn(Optional.of(URI.create("http://redirect.com/end.htm")));

        ResponseEntity<String> response = observationExecutionController.callback();

        assertEquals(HttpStatus.FOUND, response.getStatusCode());
        String location = response.getHeaders().getLocation().toString();
        assertTrue(location.contains("end.htm"));
        assertTrue(location.contains("savedid=123"));
        assertTrue(location.contains("status=200"));
    }

    @Test
    void testCallbackEndHtmSuccess() {
        ResponseEntity<String> response = observationExecutionController.callbackEndHtm();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("<html>"));
    }

    @Test
    void testCallbackFallbackSuccess() {
        RoutingInfo routingInfo = new RoutingInfo(1L, 1, OptionalInt.empty(), Set.of(), true, true);

        // No authentication
        lenient().when(authenticationFacade.getAuthentication()).thenReturn(null);

        // Parameters provided
        Map<String, String[]> paramMap = new HashMap<>();
        paramMap.put("studyid", new String[]{"1"});
        paramMap.put("observationid", new String[]{"101"});
        paramMap.put("token", new String[]{"token123"});
        lenient().when(request.getParameterMap()).thenReturn(paramMap);

        lenient().when(request.getRequestURI()).thenReturn("/api/v1/execution/callback");
        lenient().when(request.getRequestURL()).thenReturn(new StringBuffer("http://localhost:8080/api/v1/execution/callback"));
        lenient().when(request.getContextPath()).thenReturn("");
        lenient().when(request.getAttribute(org.springframework.web.servlet.HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE)).thenReturn(new HashMap<>());

        lenient().when(observationExecutionService.processCallback(any())).thenReturn(Optional.of(URI.create("http://redirect.com")));

        ResponseEntity<String> response = observationExecutionController.callback();

        assertEquals(HttpStatus.FOUND, response.getStatusCode());
        String location = response.getHeaders().getLocation().toString();
        assertTrue(location.contains("status=200"));
        verify(observationExecutionService).processCallback(any());
    }
}
