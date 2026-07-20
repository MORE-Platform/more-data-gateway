package io.redlink.more.data.service;

import io.redlink.more.data.model.DataPoint;
import io.redlink.more.data.model.Observation;
import io.redlink.more.data.model.ParticipantKeyValue;
import io.redlink.more.data.model.ParticipantWithObservationProperties;
import io.redlink.more.data.model.RoutingInfo;
import io.redlink.more.data.model.garmin.GarminUserAccessToken;
import io.redlink.more.data.model.garmin.UserAccessTokenWithData;
import io.redlink.more.data.properties.GarminProperties;
import io.redlink.more.data.repository.ParticipantKeyValueRepository;
import io.redlink.more.data.repository.StudyRepository;
import io.redlink.more.data.service.garmin.GarminService;
import org.apache.commons.lang3.Range;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.net.URI;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GarminServiceTest {

    @Mock
    private GarminProperties garminProperties;
    @Mock
    private StudyRepository studyRepository;
    @Mock
    private ParticipantKeyValueRepository participantKeyValueRepository;

    @InjectMocks
    private GarminService garminService;

    @Mock
    private ElasticService elasticService;

    private RoutingInfo routingInfo;

    @BeforeEach
    void setUp() {
        routingInfo = new RoutingInfo(1L, 10, 1, Collections.emptySet(), true, true);
    }

    @Test
    @DisplayName("getSsoUrl: returns redirectUri immediately when a valid user access token exists")
    void getSsoUrl_returnsRedirect_whenValidToken() {
        Observation garminObs = new Observation(42, 1, "Garmin", "garmin_activity", null, null, null, Instant.now(), Instant.now(), false, false, false, Set.of());
        given(studyRepository.filterObservations(eq(routingInfo), eq(true), any(), any(Set.class))).willReturn(List.of(garminObs));

        UserAccessTokenWithData valid = UserAccessTokenWithData.createNewFrom(new GarminUserAccessToken("at", "rt", "bearer", 3600, "scope", 7200));
        Map<String, Object> props = Map.of(GarminService.USER_ACCESS_TOKEN_KEY, valid.toMap());
        ParticipantWithObservationProperties pwo = new ParticipantWithObservationProperties(routingInfo.participantId(), routingInfo.studyId(), garminObs.observationId(), props);
        given(studyRepository.getParticipantObservationPropertiesByKeyExists(routingInfo.studyId(), routingInfo.participantId(), GarminService.USER_ACCESS_TOKEN_KEY))
                .willReturn(List.of(pwo));

        given(garminProperties.getRedirectUri()).willReturn("https://app.example/redirect");

        String url = garminService.getSsoUrl(routingInfo, "https://app.example/request");

        assertThat(url).isEqualTo("https://app.example/redirect");
        verify(studyRepository, never()).mergeParticipantProperties(anyLong(), anyInt(), anyInt(), anyMap());
    }

    @Test
    @DisplayName("getSsoUrl: builds OAuth URL and stores auth values when no valid token; returns URL even if HTTP fails")
    void getSsoUrl_buildsOauth_andStoresAuthValues_whenNoValidToken() {
        Observation garminObs = new Observation(7, 1, "Garmin", "garmin_connect", null, null, null, Instant.now(), Instant.now(), false, false, false, Set.of());
        given(studyRepository.filterObservations(eq(routingInfo), eq(true), any(), any(Set.class))).willReturn(List.of(garminObs));
        given(studyRepository.getParticipantObservationPropertiesByKeyExists(anyLong(), anyInt(), anyString())).willReturn(List.of());

        given(garminProperties.basicOAuthUri("https://app.example/request")).willReturn(URI.create("https://diauth.garmin.test/oauth?client_id=abc&response_type=code&redirect_uri=https://app.example/redirect"));

        String url = garminService.getSsoUrl(routingInfo, "https://app.example/request");

        assertThat(url).startsWith("https://diauth.garmin.test/oauth?client_id=abc&response_type=code")
                .contains("code_challenge=")
                .contains("code_challenge_method=S256")
                .contains("state=");
        verify(studyRepository, atLeastOnce()).mergeParticipantProperties(eq(routingInfo.studyId()), eq(routingInfo.participantId()), eq(garminObs.observationId()), anyMap());
    }

    @Test
    @DisplayName("ssoCallback: throws when no participant is found for state")
    void ssoCallback_throws_whenNoParticipantForState() {
        given(studyRepository.getParticipantByGarminStatus("state-1")).willReturn(List.of());

        assertThatThrownBy(() -> garminService.ssoCallback("state-1", "code"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No Garmin Status");
    }

    @Test
    @DisplayName("ssoCallback: throws when token exchange yields empty (e.g., missing auth values)")
    void ssoCallback_throws_whenTokenExchangeEmpty() {
        Observation garminObs = new Observation(77, 1, "Garmin", "garmin", null, null, null, Instant.now(), Instant.now(), false, false, false, Set.of());
        ParticipantWithObservationProperties pwo = new ParticipantWithObservationProperties(10, 1L, garminObs.observationId(), Map.of());
        given(studyRepository.getParticipantByGarminStatus("state-2")).willReturn(List.of(pwo));

        assertThatThrownBy(() -> garminService.ssoCallback("state-2", "code"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No Garmin Access Token");
        verify(studyRepository, never()).mergeParticipantProperties(anyLong(), anyInt(), anyInt(), anyMap());
        verify(participantKeyValueRepository, never()).insert(anyLong(), anyInt(), anyString(), anyMap());
    }

    @Test
    @DisplayName("deleteUserIdAndToken: returns true when no participants hold the userId")
    void deleteUserIdAndToken_returnsTrue_whenNoParticipants() {
        given(participantKeyValueRepository.getByKey("garmin-user-1")).willReturn(List.of());

        boolean ok = garminService.deleteUserIdAndToken("garmin-user-1");

        assertThat(ok).isTrue();
        verifyNoInteractions(studyRepository);
    }

    @Test
    @DisplayName("deleteUserIdAndToken: returns false if any delete step fails; returns true when all succeed")
    void deleteUserIdAndToken_mixedResults() {
        String userId = "user-xyz";
        ParticipantKeyValue p1 = new ParticipantKeyValue(1L, 10, userId, Map.of("keyType", "garmin"), Collections.emptySet());
        ParticipantKeyValue p2 = new ParticipantKeyValue(1L, 11, userId, Map.of("keyType", "garmin"), Collections.emptySet());
        given(participantKeyValueRepository.getByKey(userId)).willReturn(List.of(p1, p2));

        // Mock study and participant states as active so they are not filtered out
        given(studyRepository.getStudyState(1L)).willReturn(Optional.of("active"));
        given(studyRepository.getParticipantState(1L, 10)).willReturn(Optional.of("active"));
        given(studyRepository.getParticipantState(1L, 11)).willReturn(Optional.of("active"));

        given(participantKeyValueRepository.delete(1L, 10, userId, Map.of("keyType", "garmin"))).willReturn(true);
        given(participantKeyValueRepository.delete(1L, 11, userId, Map.of("keyType", "garmin"))).willReturn(false);

        Observation garminObs = new Observation(5, 1, "Garmin", "garmin", null, null, null, Instant.now(), Instant.now(), false, false, false, Set.of());
        given(studyRepository.filterObservations(eq(1L), anyInt(), any())).willReturn(List.of(garminObs));
        doNothing().when(studyRepository).removeParticipantPropertyKey(anyLong(), anyInt(), anyInt(), anyString());

        boolean result = garminService.deleteUserIdAndToken(userId);

        assertThat(result).isFalse();

        Mockito.reset(studyRepository);
        given(studyRepository.filterObservations(eq(1L), anyInt(), any())).willReturn(List.of(garminObs));
        doNothing().when(studyRepository).removeParticipantPropertyKey(anyLong(), anyInt(), anyInt(), anyString());
        // Mock study and participant states again after reset
        given(studyRepository.getStudyState(1L)).willReturn(Optional.of("active"));
        given(studyRepository.getParticipantState(1L, 10)).willReturn(Optional.of("active"));
        given(studyRepository.getParticipantState(1L, 11)).willReturn(Optional.of("active"));
        given(participantKeyValueRepository.delete(1L, 10, userId, Map.of("keyType", "garmin"))).willReturn(true);
        given(participantKeyValueRepository.delete(1L, 11, userId, Map.of("keyType", "garmin"))).willReturn(true);

        boolean resultAllOk = garminService.deleteUserIdAndToken(userId);
        assertThat(resultAllOk).isTrue();
    }

    @Test
    @DisplayName("getAllGarminParticipants: groups participants by UserAccessTokenWithData")
    void getAllGarminParticipants_groupsParticipantsByUserAccessToken() throws Exception {
        Observation garminObs = new Observation(100, 1, "Garmin", "garmin", null, null, null,
                Instant.now(), Instant.now(), false, false, false, Set.of());

        UserAccessTokenWithData token1 =
                UserAccessTokenWithData.createNewFrom(new GarminUserAccessToken("at1", "rt1", "bearer", 3600, "scope", 7200));
        Map<String, Object> props1 = Map.of(GarminService.USER_ACCESS_TOKEN_KEY, token1.toMap());

        ParticipantWithObservationProperties p1 =
                new ParticipantWithObservationProperties(10, 1L, garminObs.observationId(), props1);
        ParticipantWithObservationProperties p2 =
                new ParticipantWithObservationProperties(11, 1L, garminObs.observationId(), props1);

        UserAccessTokenWithData token2 =
                UserAccessTokenWithData.createNewFrom(new GarminUserAccessToken("at2", "rt2", "bearer", 3600, "scope", 7200));
        Map<String, Object> props2 = Map.of(GarminService.USER_ACCESS_TOKEN_KEY, token2.toMap());

        ParticipantWithObservationProperties p3 =
                new ParticipantWithObservationProperties(12, 1L, garminObs.observationId(), props2);

        given(studyRepository.getParticipantObservationPropertiesByKeyExists(eq(GarminService.USER_ACCESS_TOKEN_KEY)))
                .willReturn(List.of(p1, p2, p3));

        Method method = GarminService.class.getDeclaredMethod("getAllGarminParticipants");
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<UserAccessTokenWithData, List<ParticipantWithObservationProperties>> result =
                (Map<UserAccessTokenWithData, List<ParticipantWithObservationProperties>>) method.invoke(garminService);

        assertThat(result).hasSize(2);
        assertThat(result.values())
                .anySatisfy(list -> assertThat(list).containsExactlyInAnyOrder(p1, p2))
                .anySatisfy(list -> assertThat(list).containsExactlyInAnyOrder(p3));

        verify(studyRepository).getParticipantObservationPropertiesByKeyExists(eq(GarminService.USER_ACCESS_TOKEN_KEY));
    }

    @Test
    @DisplayName("deduplicateDataPoints: deletes existing Garmin datapoints by time ranges")
    void deduplicateDataPoints_deletesExistingByTimeRanges() throws Exception {
        DataPoint dp1 = mock(DataPoint.class);
        DataPoint dp2 = mock(DataPoint.class);

        Instant t1 = Instant.now();
        Instant t2 = t1.plusSeconds(60);

        when(dp1.effectiveDateTime()).thenReturn(t1);
        when(dp2.effectiveDateTime()).thenReturn(t2);
        when(dp1.dataType()).thenReturn("type1");
        when(dp2.dataType()).thenReturn("type2");
        when(dp1.data()).thenReturn(Map.of());
        when(dp2.data()).thenReturn(Map.of());

        RoutingInfo routingInfo = new RoutingInfo(1L, 10, 1, Collections.emptySet(), true, true);

        when(elasticService.deleteDataPointsInTimeRanges(
                eq(routingInfo),
                anyString(),
                any(Set.class)
        )).thenReturn(2L);

        Method method = GarminService.class.getDeclaredMethod("deduplicateDataPoints", List.class, RoutingInfo.class);
        method.setAccessible(true);
        method.invoke(garminService, List.of(dp1, dp2), routingInfo);

        verify(elasticService).deleteDataPointsInTimeRanges(
                eq(routingInfo),
                eq("type1"),
                argThat(set -> set.stream().anyMatch(r -> {
                    Range<Instant> rr = r;
                    return rr.contains(t1);
                }))
        );
        verify(elasticService).deleteDataPointsInTimeRanges(
                eq(routingInfo),
                eq("type2"),
                argThat(set -> set.stream().anyMatch(r -> {
                    Range<Instant> rr = r;
                    return rr.contains(t2);
                }))
        );
    }

    @Test
    @DisplayName("refreshAllTokens: completes without error when there are no Garmin participants")
    void refreshAllTokens_doesNothingWhenNoParticipants() {
        given(studyRepository.getParticipantObservationPropertiesByKeyExists(eq(GarminService.USER_ACCESS_TOKEN_KEY)))
                .willReturn(List.of());

        garminService.refreshAllTokens();

        verify(studyRepository).getParticipantObservationPropertiesByKeyExists(eq(GarminService.USER_ACCESS_TOKEN_KEY));
        verifyNoInteractions(participantKeyValueRepository);
    }

    @Test
    @DisplayName("participantForGarminUserId: returns empty list when no participants found")
    void participantForGarminUserId_returnsEmpty_whenNoParticipants() throws Exception {
        String garminUserId = "garmin-user-123";
        given(participantKeyValueRepository.getByKey(garminUserId)).willReturn(List.of());

        Method method = GarminService.class.getDeclaredMethod("participantForGarminUserId", String.class);
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<ParticipantKeyValue> result = (List<ParticipantKeyValue>) method.invoke(garminService, garminUserId);

        assertThat(result).isEmpty();
        verify(participantKeyValueRepository).getByKey(garminUserId);
        verifyNoInteractions(studyRepository);
    }

    @Test
    @DisplayName("participantForGarminUserId: returns empty list when no Garmin-type participants found")
    void participantForGarminUserId_returnsEmpty_whenNoGarminTypeParticipants() throws Exception {
        String garminUserId = "garmin-user-456";
        ParticipantKeyValue nonGarminParticipant = new ParticipantKeyValue(1L, 10, garminUserId, Map.of("keyType", "other"), Collections.emptySet());
        given(participantKeyValueRepository.getByKey(garminUserId)).willReturn(List.of(nonGarminParticipant));

        Method method = GarminService.class.getDeclaredMethod("participantForGarminUserId", String.class);
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<ParticipantKeyValue> result = (List<ParticipantKeyValue>) method.invoke(garminService, garminUserId);

        assertThat(result).isEmpty();
        verify(participantKeyValueRepository).getByKey(garminUserId);
        verifyNoInteractions(studyRepository);
    }

    @Test
    @DisplayName("participantForGarminUserId: returns only active participants")
    void participantForGarminUserId_returnsOnlyActive_whenAllParticipantsActive() throws Exception {
        String garminUserId = "garmin-user-789";
        ParticipantKeyValue p1 = new ParticipantKeyValue(1L, 10, garminUserId, Map.of("keyType", "garmin"), Collections.emptySet());
        ParticipantKeyValue p2 = new ParticipantKeyValue(1L, 11, garminUserId, Map.of("keyType", "garmin"), Collections.emptySet());
        given(participantKeyValueRepository.getByKey(garminUserId)).willReturn(List.of(p1, p2));

        given(studyRepository.getStudyState(1L)).willReturn(Optional.of("active"));
        given(studyRepository.getParticipantState(1L, 10)).willReturn(Optional.of("active"));
        given(studyRepository.getParticipantState(1L, 11)).willReturn(Optional.of("active"));

        Method method = GarminService.class.getDeclaredMethod("participantForGarminUserId", String.class);
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<ParticipantKeyValue> result = (List<ParticipantKeyValue>) method.invoke(garminService, garminUserId);

        assertThat(result).hasSize(2);
        assertThat(result).containsExactlyInAnyOrder(p1, p2);
        verify(studyRepository, times(2)).getStudyState(1L);
        verify(studyRepository).getParticipantState(1L, 10);
        verify(studyRepository).getParticipantState(1L, 11);
    }

    @Test
    @DisplayName("participantForGarminUserId: filters out inactive participants and triggers async deregistration")
    void participantForGarminUserId_filtersInactive_andTriggersAsyncDeregistration() throws Exception {
        String garminUserId = "garmin-user-abc";
        ParticipantKeyValue activeParticipant = new ParticipantKeyValue(1L, 10, garminUserId, Map.of("keyType", "garmin"), Collections.emptySet());
        ParticipantKeyValue inactiveParticipant = new ParticipantKeyValue(1L, 11, garminUserId, Map.of("keyType", "garmin"), Collections.emptySet());
        given(participantKeyValueRepository.getByKey(garminUserId)).willReturn(List.of(activeParticipant, inactiveParticipant));

        given(studyRepository.getStudyState(1L)).willReturn(Optional.of("active"));
        given(studyRepository.getParticipantState(1L, 10)).willReturn(Optional.of("active"));
        given(studyRepository.getParticipantState(1L, 11)).willReturn(Optional.of("inactive"));

        Method method = GarminService.class.getDeclaredMethod("participantForGarminUserId", String.class);
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<ParticipantKeyValue> result = (List<ParticipantKeyValue>) method.invoke(garminService, garminUserId);

        assertThat(result).hasSize(1);
        assertThat(result).containsExactly(activeParticipant);
        assertThat(result).doesNotContain(inactiveParticipant);
        verify(studyRepository, times(2)).getStudyState(1L);
        verify(studyRepository).getParticipantState(1L, 10);
        verify(studyRepository).getParticipantState(1L, 11);
    }

    @Test
    @DisplayName("participantForGarminUserId: returns empty when all participants are inactive")
    void participantForGarminUserId_returnsEmpty_whenAllParticipantsInactive() throws Exception {
        String garminUserId = "garmin-user-def";
        ParticipantKeyValue p1 = new ParticipantKeyValue(1L, 10, garminUserId, Map.of("keyType", "garmin"), Collections.emptySet());
        ParticipantKeyValue p2 = new ParticipantKeyValue(1L, 11, garminUserId, Map.of("keyType", "garmin"), Collections.emptySet());
        given(participantKeyValueRepository.getByKey(garminUserId)).willReturn(List.of(p1, p2));

        given(studyRepository.getStudyState(1L)).willReturn(Optional.of("active"));
        given(studyRepository.getParticipantState(1L, 10)).willReturn(Optional.of("locked"));
        given(studyRepository.getParticipantState(1L, 11)).willReturn(Optional.of("deleted"));

        Method method = GarminService.class.getDeclaredMethod("participantForGarminUserId", String.class);
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<ParticipantKeyValue> result = (List<ParticipantKeyValue>) method.invoke(garminService, garminUserId);

        assertThat(result).isEmpty();
        verify(studyRepository, times(2)).getStudyState(1L);
        verify(studyRepository).getParticipantState(1L, 10);
        verify(studyRepository).getParticipantState(1L, 11);
    }

    @Test
    @DisplayName("participantForGarminUserId: filters out participants with null state")
    void participantForGarminUserId_filtersOut_participantsWithNullState() throws Exception {
        String garminUserId = "garmin-user-ghi";
        ParticipantKeyValue activeParticipant = new ParticipantKeyValue(1L, 10, garminUserId, Map.of("keyType", "garmin"), Collections.emptySet());
        ParticipantKeyValue nullStateParticipant = new ParticipantKeyValue(1L, 11, garminUserId, Map.of("keyType", "garmin"), Collections.emptySet());
        given(participantKeyValueRepository.getByKey(garminUserId)).willReturn(List.of(activeParticipant, nullStateParticipant));

        given(studyRepository.getStudyState(1L)).willReturn(Optional.of("active"));
        given(studyRepository.getParticipantState(1L, 10)).willReturn(Optional.of("active"));
        given(studyRepository.getParticipantState(1L, 11)).willReturn(Optional.empty());

        Method method = GarminService.class.getDeclaredMethod("participantForGarminUserId", String.class);
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<ParticipantKeyValue> result = (List<ParticipantKeyValue>) method.invoke(garminService, garminUserId);

        assertThat(result).hasSize(1);
        assertThat(result).containsExactly(activeParticipant);
        verify(studyRepository, times(2)).getStudyState(1L);
        verify(studyRepository).getParticipantState(1L, 10);
        verify(studyRepository).getParticipantState(1L, 11);
    }

    @Test
    @DisplayName("participantForGarminUserId: handles mixed active and non-active participants correctly")
    void participantForGarminUserId_handlesMixedStates_correctly() throws Exception {
        String garminUserId = "garmin-user-jkl";
        ParticipantKeyValue active1 = new ParticipantKeyValue(1L, 10, garminUserId, Map.of("keyType", "garmin"), Collections.emptySet());
        ParticipantKeyValue active2 = new ParticipantKeyValue(2L, 20, garminUserId, Map.of("keyType", "garmin"), Collections.emptySet());
        ParticipantKeyValue inactive1 = new ParticipantKeyValue(1L, 11, garminUserId, Map.of("keyType", "garmin"), Collections.emptySet());
        ParticipantKeyValue inactive2 = new ParticipantKeyValue(2L, 21, garminUserId, Map.of("keyType", "garmin"), Collections.emptySet());
        ParticipantKeyValue nullState = new ParticipantKeyValue(3L, 30, garminUserId, Map.of("keyType", "garmin"), Collections.emptySet());

        given(participantKeyValueRepository.getByKey(garminUserId))
                .willReturn(List.of(active1, active2, inactive1, inactive2, nullState));

        given(studyRepository.getStudyState(1L)).willReturn(Optional.of("active"));
        given(studyRepository.getStudyState(2L)).willReturn(Optional.of("active"));
        given(studyRepository.getStudyState(3L)).willReturn(Optional.of("active"));
        given(studyRepository.getParticipantState(1L, 10)).willReturn(Optional.of("active"));
        given(studyRepository.getParticipantState(2L, 20)).willReturn(Optional.of("active"));
        given(studyRepository.getParticipantState(1L, 11)).willReturn(Optional.of("paused"));
        given(studyRepository.getParticipantState(2L, 21)).willReturn(Optional.of("locked"));
        given(studyRepository.getParticipantState(3L, 30)).willReturn(Optional.empty());

        Method method = GarminService.class.getDeclaredMethod("participantForGarminUserId", String.class);
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<ParticipantKeyValue> result = (List<ParticipantKeyValue>) method.invoke(garminService, garminUserId);

        assertThat(result).hasSize(2);
        assertThat(result).containsExactlyInAnyOrder(active1, active2);
        assertThat(result).doesNotContain(inactive1, inactive2, nullState);
    }

    @Test
    @DisplayName("participantForGarminUserId: filters out active participant when study is inactive")
    void participantForGarminUserId_filtersOut_whenStudyInactive() throws Exception {
        String garminUserId = "garmin-user-mno";
        ParticipantKeyValue participant = new ParticipantKeyValue(1L, 10, garminUserId, Map.of("keyType", "garmin"), Collections.emptySet());
        given(participantKeyValueRepository.getByKey(garminUserId)).willReturn(List.of(participant));

        given(studyRepository.getStudyState(1L)).willReturn(Optional.of("paused"));

        Method method = GarminService.class.getDeclaredMethod("participantForGarminUserId", String.class);
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<ParticipantKeyValue> result = (List<ParticipantKeyValue>) method.invoke(garminService, garminUserId);

        assertThat(result).isEmpty();
        verify(studyRepository).getStudyState(1L);
        verify(studyRepository, never()).getParticipantState(anyLong(), anyInt());
    }

    @Test
    @DisplayName("participantForGarminUserId: filters out active participant when study state is null")
    void participantForGarminUserId_filtersOut_whenStudyStateNull() throws Exception {
        String garminUserId = "garmin-user-pqr";
        ParticipantKeyValue participant = new ParticipantKeyValue(1L, 10, garminUserId, Map.of("keyType", "garmin"), Collections.emptySet());
        given(participantKeyValueRepository.getByKey(garminUserId)).willReturn(List.of(participant));

        given(studyRepository.getStudyState(1L)).willReturn(Optional.empty());

        Method method = GarminService.class.getDeclaredMethod("participantForGarminUserId", String.class);
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<ParticipantKeyValue> result = (List<ParticipantKeyValue>) method.invoke(garminService, garminUserId);

        assertThat(result).isEmpty();
        verify(studyRepository).getStudyState(1L);
        verify(studyRepository, never()).getParticipantState(anyLong(), anyInt());
    }

    @Test
    @DisplayName("participantForGarminUserId: filters by study state across multiple studies")
    void participantForGarminUserId_filtersByStudyState_acrossMultipleStudies() throws Exception {
        String garminUserId = "garmin-user-stu";
        ParticipantKeyValue activeStudyActiveParticipant = new ParticipantKeyValue(1L, 10, garminUserId, Map.of("keyType", "garmin"), Collections.emptySet());
        ParticipantKeyValue inactiveStudyActiveParticipant = new ParticipantKeyValue(2L, 20, garminUserId, Map.of("keyType", "garmin"), Collections.emptySet());
        ParticipantKeyValue nullStudyActiveParticipant = new ParticipantKeyValue(3L, 30, garminUserId, Map.of("keyType", "garmin"), Collections.emptySet());

        given(participantKeyValueRepository.getByKey(garminUserId))
                .willReturn(List.of(activeStudyActiveParticipant, inactiveStudyActiveParticipant, nullStudyActiveParticipant));

        given(studyRepository.getStudyState(1L)).willReturn(Optional.of("active"));
        given(studyRepository.getStudyState(2L)).willReturn(Optional.of("closed"));
        given(studyRepository.getStudyState(3L)).willReturn(Optional.empty());
        given(studyRepository.getParticipantState(1L, 10)).willReturn(Optional.of("active"));

        Method method = GarminService.class.getDeclaredMethod("participantForGarminUserId", String.class);
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<ParticipantKeyValue> result = (List<ParticipantKeyValue>) method.invoke(garminService, garminUserId);

        assertThat(result).hasSize(1);
        assertThat(result).containsExactly(activeStudyActiveParticipant);
        assertThat(result).doesNotContain(inactiveStudyActiveParticipant, nullStudyActiveParticipant);
        verify(studyRepository).getStudyState(1L);
        verify(studyRepository).getStudyState(2L);
        verify(studyRepository).getStudyState(3L);
        verify(studyRepository).getParticipantState(1L, 10);
        verify(studyRepository, never()).getParticipantState(2L, 20);
        verify(studyRepository, never()).getParticipantState(3L, 30);
    }
}