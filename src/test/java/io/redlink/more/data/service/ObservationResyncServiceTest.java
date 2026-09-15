package io.redlink.more.data.service;

import io.redlink.more.data.model.Observation;
import io.redlink.more.data.model.ObservationResyncRequest;
import io.redlink.more.data.model.RoutingInfo;
import io.redlink.more.data.repository.ObservationResyncRepository;
import io.redlink.more.data.repository.StudyRepository;
import io.redlink.more.data.service.observations.limesurvey.LimeSurveyComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

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

    private static final ObservationResyncRequest REQUEST =
            new ObservationResyncRequest(1L, 2, 3, "lime-survey-observation");
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
        when(resyncRepository.listPending()).thenReturn(List.of(REQUEST));
        when(studyRepository.getRoutingInfo(1L, 2)).thenReturn(Optional.of(ROUTING_INFO));
        when(studyRepository.filterObservations(eq(1L), eq(2), any()))
                .thenReturn(List.of(new Observation(3, null, "Survey", "lime-survey-observation", null,
                        PROPERTIES, null, null, null, false, false, false, Set.of())));
        service = new ObservationResyncService(resyncRepository, studyRepository, limeSurveyComponent);
    }

    @Test
    void deletesTheRequestOnceTheDataWasSynced() {
        when(limeSurveyComponent.resync(eq(ROUTING_INFO), eq(3), eq(PROPERTIES))).thenReturn(2);

        service.processPendingRequests();

        verify(resyncRepository).delete(REQUEST);
    }

    @Test
    void keepsTheRequestAndBacksOffWhenNothingWasSynced() {
        when(limeSurveyComponent.resync(eq(ROUTING_INFO), eq(3), eq(PROPERTIES))).thenReturn(0);

        service.processPendingRequests();
        service.processPendingRequests(); // within the 10 minute backoff

        verify(resyncRepository, never()).delete(REQUEST);
        verify(limeSurveyComponent, times(1)).resync(eq(ROUTING_INFO), eq(3), eq(PROPERTIES));
    }

    @Test
    void keepsTheRequestWhenTheResyncThrows() {
        when(limeSurveyComponent.resync(eq(ROUTING_INFO), eq(3), eq(PROPERTIES)))
                .thenThrow(new IllegalStateException("LimeSurvey down"));

        service.processPendingRequests();

        verify(resyncRepository, never()).delete(REQUEST);
    }

    @Test
    void skipsObservationTypesOtherThanLimeSurvey() {
        when(resyncRepository.listPending())
                .thenReturn(List.of(new ObservationResyncRequest(1L, 2, 3, "polar-verity-observation")));

        service.processPendingRequests();

        verify(limeSurveyComponent, never()).resync(any(), anyInt(), any());
        verify(resyncRepository, never()).delete(any());
    }
}
