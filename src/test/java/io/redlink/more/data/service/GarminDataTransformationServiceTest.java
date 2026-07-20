package io.redlink.more.data.service;

import io.redlink.more.data.custom.model.GarminDataPoint;
import io.redlink.more.data.model.DataPoint;
import io.redlink.more.data.model.DataType;
import io.redlink.more.data.model.Observation;
import io.redlink.more.data.model.ParticipantKeyValue;
import io.redlink.more.data.model.RoutingInfo;
import io.redlink.more.data.model.SimpleParticipant;
import io.redlink.more.data.model.garmin.GarminSummaryType;
import io.redlink.more.data.model.garmin.ParticipantGarminDataPoint;
import io.redlink.more.data.model.scheduler.Event;
import io.redlink.more.data.repository.StudyRepository;
import io.redlink.more.data.service.garmin.GarminDataTransformationService;
import io.redlink.more.data.service.garmin.GarminService;
import io.redlink.more.data.transformers.garmin.HeartRateTransformers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class GarminDataTransformationServiceTest extends AbstractGarminTransformerTestBase<HeartRateTransformers> {

    @Mock
    private StudyRepository studyRepository;

    private GarminDataTransformationService service;

    private final ParticipantKeyValue participantKey =
            new ParticipantKeyValue(1L, 1, "abc", Collections.emptyMap(), Collections.emptySet());

    @BeforeEach
    void setUp() {
        service = new GarminDataTransformationService(
                studyRepository,
                List.of(transformer)
        );
    }

    @Override
    protected HeartRateTransformers createTransformer() {
        return new HeartRateTransformers();
    }

    private GarminDataPoint createGarminDataPoint(String userId, String summarId, Integer startTime, Integer offset, Integer duration, Map<String, Integer> hrSamples) {
        GarminDataPoint dto = new GarminDataPoint();
        dto.setUserId(userId);
        dto.setSummaryId(summarId);
        dto.setStartTimeInSeconds(startTime);
        dto.setStartTimeOffsetInSeconds(offset);
        dto.setDurationInSeconds(duration);
        dto.setTimeOffsetHeartRateSamples(hrSamples);
        return dto;
    }


    @Test
    @DisplayName("transformData(dailies): builds HEARTRATE DataPoints per participant/observation and inserts gap sentinels")
    void transformData_dailiesWithHrSamples_buildsDataPointsAndGapSentinels() {
        Event schedule = new Event()
                .setDateStart(Instant.parse("2024-01-01T00:00:00Z"))
                .setDateEnd(Instant.parse("2024-12-31T23:59:59Z"));

        Observation garminObs = new Observation(
                1, 1, "Garmin", "garmin-heart-rate-observation",
                null, null, schedule, Instant.now(), Instant.now(), false, false, false,
                Set.of()
        );

        SimpleParticipant simpleParticipant = new SimpleParticipant(
                1, "1",
                Instant.parse("2024-01-01T00:00:00Z"),
                Instant.parse("2024-12-31T23:59:59Z")
        );

        given(studyRepository.findParticipant(any(RoutingInfo.class)))
                .willReturn(Optional.of(simpleParticipant));

        given(studyRepository.getObservationsByTypes(any(RoutingInfo.class), any(Set.class), any(Set.class)))
                .willReturn(List.of(garminObs));

        Map<String, Integer> hr = new LinkedHashMap<>();
        hr.put("0", 70);
        hr.put("10", 72);
        hr.put("40", 90);
        GarminDataPoint dto = createGarminDataPoint("abc", "sum-1", (int) Instant.parse("2024-01-02T00:00:00Z").getEpochSecond(), 0, 0, hr);
        dto.setTimeOffsetHeartRateSamples(hr);

        ParticipantGarminDataPoint pgdp = new ParticipantGarminDataPoint(participantKey, List.of(dto));

        Map<RoutingInfo, List<DataPoint>> result = service.transformData(GarminSummaryType.DAILIES, List.of(pgdp));

        assertThat(result).hasSize(1);
        List<DataPoint> produced = result.values().iterator().next();

        assertThat(produced).hasSize(3);

        assertThat(produced).allSatisfy(dp -> {
            assertThat(dp).isNotNull();
            assertThat(dp.observationType()).contains(GarminService.GARMIN_KEY_TYPE);
            assertThat(dp.dataType()).isEqualTo(DataType.HEARTRATE.name());
            assertThat(dp.effectiveDateTime()).isNotNull();
            assertThat(dp.serverTime()).isNotNull();
            assertThat(dp.data()).isInstanceOf(Map.class);
        });

        verify(studyRepository, times(1)).findParticipant(any(RoutingInfo.class));
        verify(studyRepository, times(1)).getObservationsByTypes(any(RoutingInfo.class), any(), any(Set.class));
    }


    @Test
    @DisplayName("transformData: returns empty when summaryType != dailies or when HR samples are empty")
    void transformData_nonDailiesOrNoHr_yieldsEmptyMap() {
        GarminDataPoint dto = createGarminDataPoint("abc", "sum-1", (int) Instant.parse("2024-01-01T00:00:00Z").getEpochSecond(), 0, 0, Collections.emptyMap());
        ParticipantGarminDataPoint pgdp = new ParticipantGarminDataPoint(participantKey, List.of(dto));

        Map<RoutingInfo, List<DataPoint>> notDailies = service.transformData(GarminSummaryType.EPOCHS, List.of(pgdp));
        assertThat(notDailies).isEmpty();

        Map<RoutingInfo, List<DataPoint>> emptyHr = service.transformData(GarminSummaryType.DAILIES, List.of(pgdp));
        assertThat(emptyHr).isEmpty();
    }

}