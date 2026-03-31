package io.redlink.more.data.elastic.model;

import io.redlink.more.data.model.DataPoint;
import io.redlink.more.data.model.RoutingInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ElasticDataPointExplodeTest {

    private static final Instant POLAR_EPOCH = Instant.parse("2000-01-01T00:00:00Z");

    private final RoutingInfo routingWithGroup = new RoutingInfo(1L, 10, 2, Set.of(), true, true);
    private final RoutingInfo routingNoGroup  = new RoutingInfo(1L, 10, -1, Set.of(), true, true);

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private DataPoint makeDataPoint(String id, Map<String, Object> data) {
        return new DataPoint(id, "obs-1", "polar-hr", "HR",
                Instant.now(), Instant.now(), data);
    }

    /** HR raw item – note the field is "ts", not "timestamp" */
    private Map<String, Object> hrEntry(long nanos, int hr, boolean skinContact) {
        return Map.of("ts", nanos, "hr", hr, "skinContact", skinContact);
    }

    private Map<String, Object> accEntry(long nanos, int x, int y, int z) {
        return Map.of("timestamp", nanos, "x", x, "y", y, "z", z);
    }

    private Map<String, Object> tempEntry(long nanos, float temp) {
        return Map.of("timestamp", nanos, "temp", temp);
    }

    private Map<String, Object> ppiEntry(long nanos, int hr, int ppiMs, int ppiErr, boolean skinContact) {
        return Map.of("timestamp", nanos, "hr", hr, "ppiInMs", ppiMs, "ppiErrorEstimate", ppiErr, "skinContact", skinContact);
    }

    // -----------------------------------------------------------------------
    // No "polar360*" key → empty list
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("explode_toElastic: returns empty list when no key contains 'polar360'")
    void returnsEmpty_whenNoPolar360Key() {
        DataPoint dp = makeDataPoint("dp-1", Map.of("hr_data", List.of(hrEntry(0L, 70, true))));
        List<ElasticDataPoint> result = ElasticDataPoint.explode_toElastic(dp, routingWithGroup);
        assertThat(result).isEmpty();
    }

    // -----------------------------------------------------------------------
    // HR data
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("explode_toElastic: HR – first item keeps original datapointId, rest get UUID suffix")
    void hrData_firstItemKeepsOriginalId() {
        long nanos1 = 1_000_000_000L;
        long nanos2 = 2_000_000_000L;

        DataPoint dp = makeDataPoint("dp-hr", Map.of(
                "polar360hrdata", List.of(hrEntry(nanos1, 72, true), hrEntry(nanos2, 75, false))
        ));

        List<ElasticDataPoint> result = ElasticDataPoint.explode_toElastic(dp, routingWithGroup);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).datapointId()).isEqualTo("dp-hr");
        assertThat(result.get(1).datapointId()).startsWith("dp-hr");
        assertThat(result.get(1).datapointId()).isNotEqualTo("dp-hr");
    }

    @Test
    @DisplayName("explode_toElastic: HR – data map contains 'hr' value")
    void hrData_containsHrField() {
        DataPoint dp = makeDataPoint("dp-hr", Map.of(
                "polar360hrdata", List.of(hrEntry(500_000_000L, 80, true))
        ));

        List<ElasticDataPoint> result = ElasticDataPoint.explode_toElastic(dp, routingWithGroup);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).data()).containsEntry("hr", 80);
    }

    @Test
    @DisplayName("explode_toElastic: HR – timestamp is POLAR_EPOCH + nanos")
    void hrData_timestampCalculatedFromPolarEpoch() {
        long nanos = 1_000_000_000L; // 1 second in nanos
        Instant expectedTs = POLAR_EPOCH.plusNanos(nanos);

        DataPoint dp = makeDataPoint("dp-ts", Map.of(
                "polar360hrdata", List.of(hrEntry(nanos, 65, true))
        ));

        List<ElasticDataPoint> result = ElasticDataPoint.explode_toElastic(dp, routingWithGroup);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).effectiveTimeFrame()).isEqualTo(expectedTs);
        assertThat(result.get(0).storageDate()).isEqualTo(expectedTs);
    }

    @Test
    @DisplayName("explode_toElastic: HR – routing fields are set correctly")
    void hrData_routingFieldsCorrect() {
        DataPoint dp = makeDataPoint("dp-r", Map.of(
                "polar360hrdata", List.of(hrEntry(0L, 60, true))
        ));

        List<ElasticDataPoint> result = ElasticDataPoint.explode_toElastic(dp, routingWithGroup);

        assertThat(result).hasSize(1);
        ElasticDataPoint edp = result.get(0);
        assertThat(edp.participantId()).isEqualTo("participant_10");
        assertThat(edp.studyId()).isEqualTo("study_1");
        assertThat(edp.studyGroupId()).isEqualTo("study_group_2");
    }

    @Test
    @DisplayName("explode_toElastic: HR – studyGroupId is null when routingInfo has no group")
    void hrData_studyGroupNullWhenNoGroup() {
        DataPoint dp = makeDataPoint("dp-ng", Map.of(
                "polar360hrdata", List.of(hrEntry(0L, 60, true))
        ));

        List<ElasticDataPoint> result = ElasticDataPoint.explode_toElastic(dp, routingNoGroup);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).studyGroupId()).isNull();
    }

    @Test
    @DisplayName("explode_toElastic: HR – filters entries missing ts or hr")
    void hrData_filtersIncompleteEntries() {
        // HashMap needed because Map.of disallows null values
        Map<String, Object> missingHr = new HashMap<>();
        missingHr.put("ts", 1L);

        Map<String, Object> missingTs = new HashMap<>();
        missingTs.put("hr", 70);

        DataPoint dp = makeDataPoint("dp-filter", Map.of(
                "polar360hrdata", List.of(missingHr, missingTs, hrEntry(100L, 90, true))
        ));

        List<ElasticDataPoint> result = ElasticDataPoint.explode_toElastic(dp, routingWithGroup);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).data()).containsEntry("hr", 90);
    }

    @Test
    @DisplayName("explode_toElastic: HR – observation metadata preserved on each point")
    void hrData_observationMetadataPreserved() {
        DataPoint dp = makeDataPoint("dp-meta", Map.of(
                "polar360hrdata", List.of(hrEntry(0L, 70, true))
        ));

        List<ElasticDataPoint> result = ElasticDataPoint.explode_toElastic(dp, routingWithGroup);

        assertThat(result).hasSize(1);
        ElasticDataPoint edp = result.get(0);
        assertThat(edp.observationId()).isEqualTo("obs-1");
        assertThat(edp.observationType()).isEqualTo("polar-hr");
        assertThat(edp.dataType()).isEqualTo("HR");
    }

    // -----------------------------------------------------------------------
    // ACC data
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("explode_toElastic: ACC – data map contains x, y, z")
    void accData_containsXYZ() {
        DataPoint dp = makeDataPoint("dp-acc", Map.of(
                "polar360accdata", List.of(accEntry(0L, 1, -2, 3))
        ));

        List<ElasticDataPoint> result = ElasticDataPoint.explode_toElastic(dp, routingWithGroup);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).data())
                .containsEntry("x", 1)
                .containsEntry("y", -2)
                .containsEntry("z", 3);
    }

    @Test
    @DisplayName("explode_toElastic: ACC – filters entries missing any axis or timestamp")
    void accData_filtersIncompleteEntries() {
        Map<String, Object> missingZ = new HashMap<>();
        missingZ.put("timestamp", 1L);
        missingZ.put("x", 1);
        missingZ.put("y", 2);

        DataPoint dp = makeDataPoint("dp-acc-f", Map.of(
                "polar360accdata", List.of(missingZ, accEntry(2L, 4, 5, 6))
        ));

        List<ElasticDataPoint> result = ElasticDataPoint.explode_toElastic(dp, routingWithGroup);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).data()).containsEntry("x", 4);
    }

    // -----------------------------------------------------------------------
    // TEMP data
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("explode_toElastic: TEMP – data map contains 'temperature'")
    void tempData_containsTemperatureField() {
        DataPoint dp = makeDataPoint("dp-temp", Map.of(
                "polar360tempdata", List.of(tempEntry(0L, 36.6f))
        ));

        List<ElasticDataPoint> result = ElasticDataPoint.explode_toElastic(dp, routingWithGroup);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).data()).containsKey("temperature");
        assertThat(((Number) result.get(0).data().get("temperature")).floatValue()).isEqualTo(36.6f);
    }

    @Test
    @DisplayName("explode_toElastic: TEMP – filters entries missing timestamp or temp")
    void tempData_filtersIncompleteEntries() {
        Map<String, Object> missingTemp = new HashMap<>();
        missingTemp.put("timestamp", 1L);

        DataPoint dp = makeDataPoint("dp-temp-f", Map.of(
                "polar360tempdata", List.of(missingTemp, tempEntry(2L, 37.0f))
        ));

        List<ElasticDataPoint> result = ElasticDataPoint.explode_toElastic(dp, routingWithGroup);
        assertThat(result).hasSize(1);
    }

    // -----------------------------------------------------------------------
    // PPI data
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("explode_toElastic: PPI – data map contains hr, ppiInMs, ppiErrorEstimate")
    void ppiData_containsAllFields() {
        DataPoint dp = makeDataPoint("dp-ppi", Map.of(
                "polar360ppidata", List.of(ppiEntry(0L, 65, 900, 15, true))
        ));

        List<ElasticDataPoint> result = ElasticDataPoint.explode_toElastic(dp, routingWithGroup);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).data())
                .containsEntry("hr", 65)
                .containsEntry("ppiInMs", 900)
                .containsEntry("ppiErrorEstimate", 15);
    }

    @Test
    @DisplayName("explode_toElastic: PPI – filters entries with missing fields")
    void ppiData_filtersIncompleteEntries() {
        Map<String, Object> missingErr = new HashMap<>();
        missingErr.put("timestamp", 1L);
        missingErr.put("hr", 65);
        missingErr.put("ppiInMs", 900);

        DataPoint dp = makeDataPoint("dp-ppi-f", Map.of(
                "polar360ppidata", List.of(missingErr, ppiEntry(2L, 70, 800, 10, false))
        ));

        List<ElasticDataPoint> result = ElasticDataPoint.explode_toElastic(dp, routingWithGroup);
        assertThat(result).hasSize(1);
    }

    // -----------------------------------------------------------------------
    // Mixed / edge cases
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("explode_toElastic: mixed HR + ACC – all points returned, first overall keeps original id")
    void mixed_hrAndAcc_allPointsReturned() {
        DataPoint dp = makeDataPoint("dp-mix", Map.of(
                "polar360hrdata",  List.of(hrEntry(100L, 72, true), hrEntry(200L, 74, false)),
                "polar360accdata", List.of(accEntry(300L, 0, 1, 2))
        ));

        List<ElasticDataPoint> result = ElasticDataPoint.explode_toElastic(dp, routingWithGroup);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).datapointId()).isEqualTo("dp-mix");
        result.subList(1, result.size()).forEach(edp ->
                assertThat(edp.datapointId()).startsWith("dp-mix")
        );
    }

    @Test
    @DisplayName("explode_toElastic: empty sub-lists produce no points")
    void emptySubLists_produceNoPoints() {
        DataPoint dp = makeDataPoint("dp-empty", Map.of(
                "polar360hrdata",   Collections.emptyList(),
                "polar360accdata",  Collections.emptyList(),
                "polar360tempdata", Collections.emptyList(),
                "polar360ppidata",  Collections.emptyList()
        ));

        List<ElasticDataPoint> result = ElasticDataPoint.explode_toElastic(dp, routingWithGroup);
        assertThat(result).isEmpty();
    }
}