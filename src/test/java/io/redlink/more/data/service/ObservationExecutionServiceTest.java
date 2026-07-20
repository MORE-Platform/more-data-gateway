package io.redlink.more.data.service;

import io.redlink.more.data.exception.ForbiddenException;
import io.redlink.more.data.exception.NotFoundException;
import io.redlink.more.data.model.CallbackResult;
import io.redlink.more.data.model.Observation;
import io.redlink.more.data.model.RoutingInfo;
import io.redlink.more.data.model.Study;
import io.redlink.more.data.model.scheduler.Event;
import io.redlink.more.data.service.observations.ObservationComponent;
import io.redlink.more.data.store.observationCallback.ObservationCallbackStore;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ObservationExecutionServiceTest {

    @Mock
    private StudyService studyService;

    @Mock
    private ObservationComponent observationComponent;

    @Mock
    ObservationCallbackStore callbackStore;

    private ObservationExecutionService observationExecutionService;

    @BeforeEach
    void setUp() {
        when(observationComponent.getObservationType()).thenReturn("test-type");
        observationExecutionService = new ObservationExecutionService(studyService, callbackStore, List.of(observationComponent));
    }

    @Test
    void testExecuteObservationSuccess() {
        String observationId = "1";
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant start = now.plus(1, ChronoUnit.HOURS);
        Instant end = start.plus(1, ChronoUnit.HOURS);
        RoutingInfo routingInfo = new RoutingInfo(1L, 1, OptionalInt.empty(), Set.of(), true, true);

        Event event = new Event();
        event.setDateStart(start);
        event.setDateEnd(end);

        Observation observation = new Observation(1, 1, "Title", "test-type", "Info", null, event, now, now, false, false, false, Set.of());
        Study study = new Study(1L, "Title", true, "Info", "Finish", "active", "Consent", null, null, null, null, List.of(observation), now, now, null);

        when(studyService.getStudy(routingInfo)).thenReturn(Optional.of(Pair.of(study, Collections.emptyList())));
        when(observationComponent.produceUrl(any(), any(), any(), any())).thenReturn(Optional.of("http://test.com"));

        String url = observationExecutionService.executeObservation(observationId, start, end, routingInfo, null).map(URI::toString).orElse(null);

        assertEquals("http://test.com", url);
    }

    @Test
    void testExecuteObservationPreviewSuccess() {
        String observationId = "1";
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant start = now.plus(1, ChronoUnit.HOURS);
        Instant end = start.plus(1, ChronoUnit.HOURS);
        RoutingInfo routingInfo = new RoutingInfo(1L, 1, OptionalInt.empty(), Set.of(), true, true);

        Event event = new Event();
        event.setDateStart(start);
        event.setDateEnd(end);

        Observation observation = new Observation(1, 1, "Title", "test-type", "Info", null, event, now, now, false, false, false, Set.of());
        Study study = new Study(1L, "Title", true, "Info", "Finish", "preview", "Consent", null, null, null, null, List.of(observation), now, now, null);

        when(studyService.getStudy(routingInfo)).thenReturn(Optional.of(Pair.of(study, Collections.emptyList())));
        when(observationComponent.produceUrl(any(), any(), any(), any())).thenReturn(Optional.of("http://test.com"));

        String url = observationExecutionService.executeObservation(observationId, start, end, routingInfo, null).map(URI::toString).orElse(null);

        assertEquals("http://test.com", url);
    }

    @Test
    void testExecuteObservationInvalidStatus() {
        String observationId = "1";
        Instant now = Instant.now();
        RoutingInfo routingInfo = new RoutingInfo(1L, 1, OptionalInt.empty(), Set.of(), true, true);

        Study study = new Study(1L, "Title", true, "Info", "Finish", "inactive", "Consent", null, null, null, null, List.of(), now, now, null);

        when(studyService.getStudy(routingInfo)).thenReturn(Optional.of(Pair.of(study, Collections.emptyList())));

        assertThrows(ForbiddenException.class, () -> observationExecutionService.executeObservation(observationId, now, now, routingInfo, null));
    }

    @Test
    void testExecuteObservationNotFound() {
        String observationId = "1";
        RoutingInfo routingInfo = new RoutingInfo(1L, 1, OptionalInt.empty(), Set.of(), true, true);

        when(studyService.getStudy(routingInfo)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> observationExecutionService.executeObservation(observationId, Instant.now(), Instant.now(), routingInfo, null));
    }

    @Test
    void testExecuteObservationInvalidSchedule() {
        String observationId = "1";
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant start = now.plus(1, ChronoUnit.HOURS);
        Instant end = start.plus(1, ChronoUnit.HOURS);
        RoutingInfo routingInfo = new RoutingInfo(1L, 1, OptionalInt.empty(), Set.of(), true, true);

        Event event = new Event();
        event.setDateStart(start);
        event.setDateEnd(end);

        Observation observation = new Observation(1, 1, "Title", "test-type", "Info", null, event, now, now, false, false, false, Set.of());
        Study study = new Study(1L, "Title", true, "Info", "Finish", "active", "Consent", null, null, null, null, List.of(observation), now, now, null);

        when(studyService.getStudy(routingInfo)).thenReturn(Optional.of(Pair.of(study, Collections.emptyList())));

        // Wrong schedule
        assertThrows(ForbiddenException.class, () -> observationExecutionService.executeObservation(observationId, start.minusSeconds(1), end, routingInfo, null));
    }

    @Test
    void testExecuteObservationWithStudyDates() {
        String observationId = "1";
        java.time.LocalDate today = java.time.LocalDate.now();
        java.time.ZoneId zoneId = java.time.ZoneId.systemDefault();

        Instant scheduleStart = today.atTime(10, 0).atZone(zoneId).toInstant();
        Instant scheduleEnd = today.atTime(11, 0).atZone(zoneId).toInstant();
        RoutingInfo routingInfo = new RoutingInfo(1L, 1, java.util.OptionalInt.empty(), java.util.Set.of(), true, true);

        Event event = new Event();
        event.setDateStart(scheduleStart);
        event.setDateEnd(scheduleEnd);

        Observation observation = new Observation(1, 1, "Title", "test-type", "Info", null, event, Instant.now(), Instant.now(), false, false, false, java.util.Set.of());
        Study study = new Study(1L, "Title", true, "Info", "Finish", "active", "Consent", null,
                today.minusDays(1), null, today, List.of(observation), Instant.now(), Instant.now(), null);

        when(studyService.getStudy(routingInfo)).thenReturn(Optional.of(Pair.of(study, Collections.emptyList())));
        when(observationComponent.produceUrl(any(), any(), any(), any())).thenReturn(Optional.of("http://test.com"));

        String url = observationExecutionService.executeObservation(observationId, scheduleStart, scheduleEnd, routingInfo, null).map(URI::toString).orElse(null);

        assertEquals("http://test.com", url);
    }

    @Test
    void testProcessCallback() {
        RoutingInfo routingInfo = new RoutingInfo(1L, 1, OptionalInt.empty(), Set.of(), true, true);
        Map<String, String> parameters = Map.of("key", "value");

        when(observationComponent.necessaryCallbackParameters(any())).thenReturn(true);
        when(observationComponent.processCallback(any())).thenReturn(Optional.of(new CallbackResult(routingInfo, 1)));

        observationExecutionService.processCallback(parameters);

        verify(observationComponent).processCallback(eq(parameters));
    }

    @Test
    void testProcessCallbackFallback() {
        Map<String, String> parameters = Map.of("key", "value");

        when(observationComponent.necessaryCallbackParameters(any())).thenReturn(false);

        Optional<URI> result = observationExecutionService.processCallback(parameters);

        assertFalse(result.isPresent());
    }
}
