package io.redlink.more.data.service;

import io.redlink.more.data.model.Observation;
import io.redlink.more.data.model.ObservationResyncRequest;
import io.redlink.more.data.model.ResyncInterval;
import io.redlink.more.data.model.RoutingInfo;
import io.redlink.more.data.repository.ObservationResyncRepository;
import io.redlink.more.data.repository.StudyRepository;
import io.redlink.more.data.service.observations.limesurvey.LimeSurveyComponent;
import io.redlink.more.data.service.observations.limesurvey.LimeSurveyComponent.ResyncResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ObservationResyncServiceTest {

    private static final Instant START = Instant.now().minus(Duration.ofMinutes(30));
    // a portal request: every 10 minutes for 24 hours
    private static final ObservationResyncRequest RUNNING = new ObservationResyncRequest(
            1L, 2, 3, "lime-survey-observation", ResyncInterval.NORMAL, START, START.plus(Duration.ofHours(24)));
    // a request that has reached its end (end = start)
    private static final ObservationResyncRequest ONCE = new ObservationResyncRequest(
            1L, 2, 3, "lime-survey-observation", ResyncInterval.URGENT, START, START);
    private static final RoutingInfo ROUTING_INFO =
            new RoutingInfo(1L, 2, OptionalInt.empty(), Set.of(), true, true);
    // limeSurveyId comes from the observation, token from the participant - StudyRepository merges both
    private static final Map<String, Object> PROPERTIES = Map.of("limeSurveyId", "100", "token", "token123");

    @Mock
    private ObservationResyncRepository resyncRepository;
    @Mock
    private StudyRepository studyRepository;
    @Mock
    private LimeSurveyComponent limeSurveyComponent;

    private ObservationResyncService service;

    @BeforeEach
    void setUp() {
        when(limeSurveyComponent.getObservationType()).thenReturn("lime-survey-observation");
        when(studyRepository.getRoutingInfo(1L, 2)).thenReturn(Optional.of(ROUTING_INFO));
        when(studyRepository.filterObservations(eq(1L), eq(2), any()))
                .thenReturn(List.of(new Observation(3, null, "Survey", "lime-survey-observation", null,
                        PROPERTIES, null, null, null, false, false, false, Set.of())));
        service = new ObservationResyncService(resyncRepository, studyRepository, limeSurveyComponent);
    }

    @Test
    void marksTheRequestSyncedOnceANewAnswerWasCollected() {
        givenPending(RUNNING, new ResyncResult(2, true));

        service.processPendingRequests();

        verify(limeSurveyComponent).resync(ROUTING_INFO, 3, PROPERTIES, START);
        verify(resyncRepository).markSynced(RUNNING);
        verify(resyncRepository, never()).delete(any());
    }

    @Test
    void keepsARunningRequestAndWaitsItsIntervalWhenOnlyOldAnswersExist() {
        givenPending(RUNNING, new ResyncResult(1, false));

        service.processPendingRequests();
        service.processPendingRequests(); // within the 10 minute interval

        verify(limeSurveyComponent, times(1)).resync(any(), anyInt(), any(), any());
        verify(resyncRepository, never()).markSynced(any());
        verify(resyncRepository, never()).delete(any());
    }

    @Test
    void keepsARunningRequestWhenTheResyncThrows() {
        when(resyncRepository.listPending()).thenReturn(List.of(RUNNING));
        when(limeSurveyComponent.resync(any(), anyInt(), any(), any())).thenThrow(new IllegalStateException("LimeSurvey down"));

        service.processPendingRequests();

        verify(resyncRepository, never()).markSynced(any());
        verify(resyncRepository, never()).delete(any());
    }

    @Test
    void aOnceRequestThatStoredDataIsMarkedSyncedForTheHealthCheck() {
        givenPending(ONCE, new ResyncResult(1, false));

        service.processPendingRequests();

        verify(resyncRepository).markSynced(ONCE);
        verify(resyncRepository, never()).delete(any());
    }

    @Test
    void anEndedRequestWithoutDataIsDeleted() {
        givenPending(ONCE, ResyncResult.NOTHING);

        service.processPendingRequests();

        verify(resyncRepository).delete(ONCE);
        verify(resyncRepository, never()).markSynced(any());
    }

    @Test
    void skipsObservationTypesOtherThanLimeSurveyAndDeletesThemAfterTheirEnd() {
        ObservationResyncRequest polar = new ObservationResyncRequest(
                1L, 2, 3, "polar-verity-observation", ResyncInterval.URGENT, START, START);
        when(resyncRepository.listPending()).thenReturn(List.of(polar));

        service.processPendingRequests();

        verify(limeSurveyComponent, never()).resync(any(), anyInt(), any(), any());
        verify(resyncRepository).delete(polar);
    }

    private void givenPending(ObservationResyncRequest request, ResyncResult result) {
        when(resyncRepository.listPending()).thenReturn(List.of(request));
        when(limeSurveyComponent.resync(ROUTING_INFO, 3, PROPERTIES, START)).thenReturn(result);
    }
}
