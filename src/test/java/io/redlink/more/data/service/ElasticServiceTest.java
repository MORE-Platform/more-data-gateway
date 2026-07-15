package io.redlink.more.data.service;


import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.DeleteByQueryRequest;
import co.elastic.clients.elasticsearch.core.DeleteByQueryResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import io.redlink.more.data.model.DataPoint;
import io.redlink.more.data.model.RoutingInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

import static io.redlink.more.data.util.ElasticUtils.Constants.GARMIN_SUMMARY_ID_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class ElasticServiceTest {

    @Mock
    private ElasticsearchClient client;

    @InjectMocks
    private ElasticService elasticService;

    private RoutingInfo routingInfo;

    @BeforeEach
    void setUp() {
        routingInfo = new RoutingInfo(1L, 10, 1, Collections.emptySet(), true, true);
    }

    @Test
    @DisplayName("storeDataPoints: returns successfully stored ids")
    void storeDataPoints_returnsIds() throws Exception {
        DataPoint dp1 = mock(DataPoint.class);
        DataPoint dp2 = mock(DataPoint.class);

        Map<String, Object> data1 = Map.of(GARMIN_SUMMARY_ID_KEY, "sum-1");
        Map<String, Object> data2 = Map.of(GARMIN_SUMMARY_ID_KEY, "sum-2");
        when(dp1.data()).thenReturn(data1);
        when(dp2.data()).thenReturn(data2);
        when(dp1.datapointId()).thenReturn("1");
        when(dp2.datapointId()).thenReturn("2");

        List<DataPoint> bulk = List.of(dp1, dp2);

        String uidPrefix = "1_10_";
        BulkResponseItem item1 = mock(BulkResponseItem.class);
        BulkResponseItem item2 = mock(BulkResponseItem.class);
        when(item1.id()).thenReturn(uidPrefix + "1");
        when(item2.id()).thenReturn(uidPrefix + "2");
        when(item1.error()).thenReturn(null);
        when(item2.error()).thenReturn(null);

        BulkResponse bulkResponse = mock(BulkResponse.class);
        when(bulkResponse.errors()).thenReturn(false);
        when(bulkResponse.items()).thenReturn(List.of(item1, item2));

        when(client.bulk(any(BulkRequest.class))).thenReturn(bulkResponse);

        List<String> ids = elasticService.storeDataPoints(bulk, routingInfo);

        assertThat(ids).containsExactlyInAnyOrder("1", "2");
        verify(client).bulk(any(BulkRequest.class));
    }

    @Test
    @DisplayName("storeDataPoints: stores datapoints without Garmin summary ids")
    void storeDataPoints_storesWithoutGarminSummaryIds() throws Exception {
        DataPoint dp = mock(DataPoint.class);
        when(dp.data()).thenReturn(Map.of("other", "value"));
        when(dp.datapointId()).thenReturn("42");

        String uidPrefix = "1_10_";
        BulkResponseItem item = mock(BulkResponseItem.class);
        when(item.id()).thenReturn(uidPrefix + "42");
        when(item.error()).thenReturn(null);

        BulkResponse bulkResponse = mock(BulkResponse.class);
        when(bulkResponse.errors()).thenReturn(false);
        when(bulkResponse.items()).thenReturn(List.of(item));

        when(client.bulk(any(BulkRequest.class))).thenReturn(bulkResponse);

        List<String> result = elasticService.storeDataPoints(List.of(dp), routingInfo);

        assertThat(result).containsExactly("42");
        verify(client).bulk(any(BulkRequest.class));
    }

    @Test
    @DisplayName("deleteDataPoints: calls deleteByQuery and returns deleted count")
    void deleteDataPoints_callsDeleteByQueryAndReturnsDeletedCount() throws Exception {
        DeleteByQueryResponse deleteResponse = mock(DeleteByQueryResponse.class);
        when(deleteResponse.deleted()).thenReturn(2L);
        when(client.deleteByQuery(any(DeleteByQueryRequest.class))).thenReturn(deleteResponse);

        long deleted = elasticService.deleteDataPoints(
                routingInfo,
                "DAILY_STEPS", "data_" + GARMIN_SUMMARY_ID_KEY + ".keyword",
                Set.of("sum-1", "sum-2")
        );

        assertThat(deleted).isEqualTo(2L);
        verify(client).deleteByQuery(any(DeleteByQueryRequest.class));
    }

    @Test
    @DisplayName("storeDataPoints: rethrows IOException when bulk request fails")
    void storeDataPoints_rethrowsIOException_whenBulkThrows() throws Exception {
        DataPoint dp = mock(DataPoint.class);
        when(dp.data()).thenReturn(Map.of());
        when(dp.datapointId()).thenReturn("1");

        IOException io = new IOException("boom");
        when(client.bulk(any(BulkRequest.class))).thenThrow(io);

        assertThatThrownBy(() -> elasticService.storeDataPoints(List.of(dp), routingInfo))
                .isSameAs(io);

        verify(client).bulk(any(BulkRequest.class));
    }

    @Test
    @DisplayName("storeDataPoints: polar360 data returns original datapointId, not Elastic response ids")
    void storeDataPoints_polar360Data_returnsOriginalIds() throws Exception {
        DataPoint dp = mock(DataPoint.class);
        // Any key containing "polar360" triggers the explode path
        Map<String, Object> data = Map.of("polar360hrdata", List.of(Map.of("timestamp", 0L, "hr", 70, "skinContact", true)));
        when(dp.data()).thenReturn(data);
        when(dp.datapointId()).thenReturn("polar-dp-1");

        BulkResponse bulkResponse = mock(BulkResponse.class);
        when(bulkResponse.errors()).thenReturn(false);
        when(client.bulk(any(BulkRequest.class))).thenReturn(bulkResponse);

        List<String> ids = elasticService.storeDataPoints(List.of(dp), routingInfo);

        assertThat(ids).containsExactly("polar-dp-1");
        verify(client).bulk(any(BulkRequest.class));
    }

    @Test
    @DisplayName("storeDataPoints: polar360 data with no exploded items skips Elastic call and returns empty")
    void storeDataPoints_polar360Data_allEmptyProducesNoCall() throws Exception {
        DataPoint dp = mock(DataPoint.class);
        // polar360 key present, but no actual sub-lists → explode_toElastic returns empty
        Map<String, Object> data = Map.of("polar360hrdata", Collections.emptyList());
        when(dp.data()).thenReturn(data);
        when(dp.datapointId()).thenReturn("polar-dp-empty");

        List<String> ids = elasticService.storeDataPoints(List.of(dp), routingInfo);

        assertThat(ids).isEmpty();
        verify(client, never()).bulk(any(BulkRequest.class));
    }

    @Test
    @DisplayName("ElasticService private helpers: generateUidPrefix and getElasticIndexName")
    void privateHelpers_generateUidPrefix_and_getElasticIndexName() throws Exception {
        Method prefixMethod = ElasticService.class.getDeclaredMethod("generateUidPrefix", RoutingInfo.class);
        prefixMethod.setAccessible(true);
        String prefix = (String) prefixMethod.invoke(elasticService, routingInfo);

        assertThat(prefix).isEqualTo("1_10_");

        Method indexMethod = ElasticService.class.getDeclaredMethod("getElasticIndexName", RoutingInfo.class);
        indexMethod.setAccessible(true);
        String indexName = (String) indexMethod.invoke(elasticService, routingInfo);

        assertThat(indexName).isEqualTo("study_" + routingInfo.studyId());
    }

    // -----------------------------------------------------------------------
    // Batched (chunked) bulk send – BULK_CHUNK_SIZE = 2000
    // -----------------------------------------------------------------------

    // Must match ElasticService.BULK_CHUNK_SIZE (private constant).
    private static final int BULK_CHUNK_SIZE = 2000;

    /**
     * Answers a bulk request by echoing back one successful response item per operation,
     * with the item id equal to the operation's document id. This lets storeDataPoints
     * strip the uid-prefix and return the datapoint ids, so we can assert that ids from
     * *every* chunk are aggregated (not just the last one).
     */
    private void answerBulkByEchoingOperationIds() throws IOException {
        when(client.bulk(any(BulkRequest.class))).thenAnswer(invocation -> {
            BulkRequest request = invocation.getArgument(0);
            List<BulkResponseItem> items = new ArrayList<>();
            for (BulkOperation op : request.operations()) {
                BulkResponseItem item = mock(BulkResponseItem.class);
                when(item.id()).thenReturn(op.index().id());
                when(item.error()).thenReturn(null);
                items.add(item);
            }
            BulkResponse response = mock(BulkResponse.class);
            when(response.errors()).thenReturn(false);
            when(response.items()).thenReturn(items);
            return response;
        });
    }

    private List<DataPoint> nonPolarDataPoints(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> {
                    DataPoint dp = mock(DataPoint.class);
                    when(dp.data()).thenReturn(Map.of("other", "value-" + i));
                    when(dp.datapointId()).thenReturn(String.valueOf(i));
                    return dp;
                })
                .map(dp -> (DataPoint) dp)
                .toList();
    }

    @Test
    @DisplayName("storeDataPoints: splits a large non-exploded bulk into BULK_CHUNK_SIZE chunks")
    void storeDataPoints_nonExploded_splitsIntoChunks() throws Exception {
        int count = BULK_CHUNK_SIZE * 2 + 1; // 4001 -> 3 chunks (2000, 2000, 1)
        answerBulkByEchoingOperationIds();

        List<String> ids = elasticService.storeDataPoints(nonPolarDataPoints(count), routingInfo);

        ArgumentCaptor<BulkRequest> captor = ArgumentCaptor.forClass(BulkRequest.class);
        verify(client, times(3)).bulk(captor.capture());

        List<BulkRequest> requests = captor.getAllValues();
        // No chunk exceeds the limit, and the chunk sizes add up to the total (nothing dropped).
        assertThat(requests).allSatisfy(r -> assertThat(r.operations().size()).isLessThanOrEqualTo(BULK_CHUNK_SIZE));
        assertThat(requests.stream().mapToInt(r -> r.operations().size()).sum()).isEqualTo(count);
        // Ids from every chunk are aggregated into the result.
        assertThat(ids).hasSize(count);
        assertThat(ids).contains("0", String.valueOf(BULK_CHUNK_SIZE), String.valueOf(count - 1));
    }

    @Test
    @DisplayName("storeDataPoints: a full multiple of BULK_CHUNK_SIZE sends exactly N chunks (no empty trailing request)")
    void storeDataPoints_nonExploded_exactMultipleHasNoTrailingChunk() throws Exception {
        int count = BULK_CHUNK_SIZE * 2; // exactly 2 chunks, no leftover
        answerBulkByEchoingOperationIds();

        List<String> ids = elasticService.storeDataPoints(nonPolarDataPoints(count), routingInfo);

        verify(client, times(2)).bulk(any(BulkRequest.class));
        assertThat(ids).hasSize(count);
    }

    @Test
    @DisplayName("storeDataPoints: at-limit non-exploded bulk sends a single chunk")
    void storeDataPoints_nonExploded_atLimitSingleChunk() throws Exception {
        answerBulkByEchoingOperationIds();

        List<String> ids = elasticService.storeDataPoints(nonPolarDataPoints(BULK_CHUNK_SIZE), routingInfo);

        verify(client, times(1)).bulk(any(BulkRequest.class));
        assertThat(ids).hasSize(BULK_CHUNK_SIZE);
    }

    @Test
    @DisplayName("storeDataPoints: exploded polar360 samples beyond the limit are chunked; original id returned once")
    void storeDataPoints_exploded_splitsIntoChunks() throws Exception {
        // One polar360 datapoint whose HR sub-list explodes into > 2 chunks worth of items.
        int samples = BULK_CHUNK_SIZE * 2 + 5; // 4005 exploded items -> 3 chunks
        List<Map<String, Object>> hrSamples = IntStream.range(0, samples)
                .mapToObj(i -> Map.<String, Object>of("timestamp", (long) i, "hr", 70, "skinContact", true))
                .toList();

        DataPoint dp = mock(DataPoint.class);
        when(dp.data()).thenReturn(Map.of("polar360hrdata", hrSamples));
        when(dp.datapointId()).thenReturn("polar-dp-1");

        BulkResponse response = mock(BulkResponse.class);
        when(response.errors()).thenReturn(false);
        when(client.bulk(any(BulkRequest.class))).thenReturn(response);

        List<String> ids = elasticService.storeDataPoints(List.of(dp), routingInfo);

        ArgumentCaptor<BulkRequest> captor = ArgumentCaptor.forClass(BulkRequest.class);
        verify(client, times(3)).bulk(captor.capture());

        List<BulkRequest> requests = captor.getAllValues();
        assertThat(requests).allSatisfy(r -> assertThat(r.operations().size()).isLessThanOrEqualTo(BULK_CHUNK_SIZE));
        // Every exploded item is sent across the chunks – nothing is lost.
        assertThat(requests.stream().mapToInt(r -> r.operations().size()).sum()).isEqualTo(samples);
        // For exploded data the original datapoint id is returned exactly once, not the exploded ids.
        assertThat(ids).containsExactly("polar-dp-1");
    }

    @Test
    @DisplayName("storeDataPoints: exploded polar360 samples one below the limit send a single chunk")
    void storeDataPoints_exploded_oneBelowLimitSingleChunk() throws Exception {
        int samples = BULK_CHUNK_SIZE - 1; // 1999 exploded items -> 1 chunk
        List<Map<String, Object>> hrSamples = IntStream.range(0, samples)
                .mapToObj(i -> Map.<String, Object>of("timestamp", (long) i, "hr", 70, "skinContact", true))
                .toList();

        DataPoint dp = mock(DataPoint.class);
        when(dp.data()).thenReturn(Map.of("polar360hrdata", hrSamples));
        when(dp.datapointId()).thenReturn("polar-dp-1");

        BulkResponse response = mock(BulkResponse.class);
        when(response.errors()).thenReturn(false);
        when(client.bulk(any(BulkRequest.class))).thenReturn(response);

        List<String> ids = elasticService.storeDataPoints(List.of(dp), routingInfo);

        ArgumentCaptor<BulkRequest> captor = ArgumentCaptor.forClass(BulkRequest.class);
        verify(client, times(1)).bulk(captor.capture());

        assertThat(captor.getValue().operations()).hasSize(samples);
        assertThat(ids).containsExactly("polar-dp-1");
    }

    @Test
    @DisplayName("storeDataPoints: exploded polar360 samples at 10x the limit send exactly 10 chunks")
    void storeDataPoints_exploded_tenTimesLimitSendsTenChunks() throws Exception {
        int samples = BULK_CHUNK_SIZE * 10; // 20000 exploded items -> exactly 10 chunks
        List<Map<String, Object>> hrSamples = IntStream.range(0, samples)
                .mapToObj(i -> Map.<String, Object>of("timestamp", (long) i, "hr", 70, "skinContact", true))
                .toList();

        DataPoint dp = mock(DataPoint.class);
        when(dp.data()).thenReturn(Map.of("polar360hrdata", hrSamples));
        when(dp.datapointId()).thenReturn("polar-dp-1");

        BulkResponse response = mock(BulkResponse.class);
        when(response.errors()).thenReturn(false);
        when(client.bulk(any(BulkRequest.class))).thenReturn(response);

        List<String> ids = elasticService.storeDataPoints(List.of(dp), routingInfo);

        ArgumentCaptor<BulkRequest> captor = ArgumentCaptor.forClass(BulkRequest.class);
        verify(client, times(10)).bulk(captor.capture());

        List<BulkRequest> requests = captor.getAllValues();
        assertThat(requests).allSatisfy(r -> assertThat(r.operations().size()).isLessThanOrEqualTo(BULK_CHUNK_SIZE));
        // Every exploded item is sent across the chunks – nothing is lost.
        assertThat(requests.stream().mapToInt(r -> r.operations().size()).sum()).isEqualTo(samples);
        assertThat(ids).containsExactly("polar-dp-1");
    }

    private List<Map<String, Object>> ppiSamples(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> Map.<String, Object>of(
                        "hr", 70,
                        "timestamp", (long) i,
                        "ppiInMs", 800,
                        "ppiErrorEstimate", 5,
                        "skinContact", true))
                .toList();
    }

    private List<Map<String, Object>> tempSamples(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> Map.<String, Object>of(
                        "temp", 36.5d,
                        "timestamp", (long) i))
                .toList();
    }

    @Test
    @DisplayName("storeDataPoints: exploded polar360 PPI samples beyond the limit are chunked; original id returned once")
    void storeDataPoints_explodedPpi_splitsIntoChunks() throws Exception {
        int samples = BULK_CHUNK_SIZE * 2 + 5; // 4005 exploded items -> 3 chunks

        DataPoint dp = mock(DataPoint.class);
        when(dp.data()).thenReturn(Map.of("polar360ppidata", ppiSamples(samples)));
        when(dp.datapointId()).thenReturn("polar-ppi-1");

        BulkResponse response = mock(BulkResponse.class);
        when(response.errors()).thenReturn(false);
        when(client.bulk(any(BulkRequest.class))).thenReturn(response);

        List<String> ids = elasticService.storeDataPoints(List.of(dp), routingInfo);

        ArgumentCaptor<BulkRequest> captor = ArgumentCaptor.forClass(BulkRequest.class);
        verify(client, times(3)).bulk(captor.capture());

        List<BulkRequest> requests = captor.getAllValues();
        assertThat(requests).allSatisfy(r -> assertThat(r.operations().size()).isLessThanOrEqualTo(BULK_CHUNK_SIZE));
        assertThat(requests.stream().mapToInt(r -> r.operations().size()).sum()).isEqualTo(samples);
        assertThat(ids).containsExactly("polar-ppi-1");
    }

    @Test
    @DisplayName("storeDataPoints: exploded polar360 temperature samples one below the limit send a single chunk")
    void storeDataPoints_explodedTemp_oneBelowLimitSingleChunk() throws Exception {
        int samples = BULK_CHUNK_SIZE - 1; // 1999 exploded items -> 1 chunk

        DataPoint dp = mock(DataPoint.class);
        when(dp.data()).thenReturn(Map.of("polar360tempdata", tempSamples(samples)));
        when(dp.datapointId()).thenReturn("polar-temp-1");

        BulkResponse response = mock(BulkResponse.class);
        when(response.errors()).thenReturn(false);
        when(client.bulk(any(BulkRequest.class))).thenReturn(response);

        List<String> ids = elasticService.storeDataPoints(List.of(dp), routingInfo);

        ArgumentCaptor<BulkRequest> captor = ArgumentCaptor.forClass(BulkRequest.class);
        verify(client, times(1)).bulk(captor.capture());

        assertThat(captor.getValue().operations()).hasSize(samples);
        assertThat(ids).containsExactly("polar-temp-1");
    }

    @Test
    @DisplayName("storeDataPoints: a single polar360 datapoint mixing HR, PPI and temperature explodes all sample types and chunks the combined total")
    void storeDataPoints_explodedMixed_hrPpiTemp_allSamplesChunked() throws Exception {
        int hrCount = BULK_CHUNK_SIZE;       // 2000
        int ppiCount = BULK_CHUNK_SIZE / 2;  // 1000
        int tempCount = BULK_CHUNK_SIZE / 2; // 1000
        int total = hrCount + ppiCount + tempCount; // 4000 exploded items -> 2 chunks

        List<Map<String, Object>> hrSamples = IntStream.range(0, hrCount)
                .mapToObj(i -> Map.<String, Object>of("timestamp", (long) i, "hr", 70, "skinContact", true))
                .toList();

        DataPoint dp = mock(DataPoint.class);
        when(dp.data()).thenReturn(Map.of(
                "polar360hrdata", hrSamples,
                "polar360ppidata", ppiSamples(ppiCount),
                "polar360tempdata", tempSamples(tempCount)));
        when(dp.datapointId()).thenReturn("polar-mixed-1");

        BulkResponse response = mock(BulkResponse.class);
        when(response.errors()).thenReturn(false);
        when(client.bulk(any(BulkRequest.class))).thenReturn(response);

        List<String> ids = elasticService.storeDataPoints(List.of(dp), routingInfo);

        ArgumentCaptor<BulkRequest> captor = ArgumentCaptor.forClass(BulkRequest.class);
        verify(client, times(2)).bulk(captor.capture());

        List<BulkRequest> requests = captor.getAllValues();
        assertThat(requests).allSatisfy(r -> assertThat(r.operations().size()).isLessThanOrEqualTo(BULK_CHUNK_SIZE));
        // HR + PPI + temperature samples are all exploded and none are dropped across the chunks.
        assertThat(requests.stream().mapToInt(r -> r.operations().size()).sum()).isEqualTo(total);
        assertThat(ids).containsExactly("polar-mixed-1");
    }

    private DataPoint hrDataPoint(String datapointId, int sampleCount) {
        List<Map<String, Object>> hrSamples = IntStream.range(0, sampleCount)
                .mapToObj(i -> Map.<String, Object>of("timestamp", (long) i, "hr", 70, "skinContact", true))
                .toList();
        DataPoint dp = mock(DataPoint.class);
        when(dp.data()).thenReturn(Map.of("polar360hrdata", hrSamples));
        when(dp.datapointId()).thenReturn(datapointId);
        return dp;
    }

    @Test
    @DisplayName("storeDataPoints: HR samples arriving across separate requests with distinct datapoint ids get non-colliding elastic ids")
    void storeDataPoints_separateHrRequests_distinctIds_noCollision() throws Exception {
        int firstCount = 3;
        int secondCount = 4;

        BulkResponse response = mock(BulkResponse.class);
        when(response.errors()).thenReturn(false);
        when(client.bulk(any(BulkRequest.class))).thenReturn(response);

        // The device sends HR in two separate uploads; each request carries its own dataId.
        List<String> firstIds = elasticService.storeDataPoints(List.of(hrDataPoint("hr-1", firstCount)), routingInfo);
        List<String> secondIds = elasticService.storeDataPoints(List.of(hrDataPoint("hr-2", secondCount)), routingInfo);

        assertThat(firstIds).containsExactly("hr-1");
        assertThat(secondIds).containsExactly("hr-2");

        ArgumentCaptor<BulkRequest> captor = ArgumentCaptor.forClass(BulkRequest.class);
        verify(client, times(2)).bulk(captor.capture());

        // Distinct dataIds keep the exploded elastic _id prefixes apart, so nothing overwrites.
        List<String> allElasticIds = captor.getAllValues().stream()
                .flatMap(r -> r.operations().stream())
                .map(op -> op.index().id())
                .toList();
        assertThat(allElasticIds).hasSize(firstCount + secondCount);
        assertThat(allElasticIds).doesNotHaveDuplicates();
        assertThat(allElasticIds).filteredOn(id -> id.startsWith("1_10_hr-1-")).hasSize(firstCount);
        assertThat(allElasticIds).filteredOn(id -> id.startsWith("1_10_hr-2-")).hasSize(secondCount);
    }

    @Test
    @DisplayName("storeDataPoints: HR samples arriving across separate requests that reuse the same datapoint id produce colliding elastic ids (overwrite)")
    void storeDataPoints_separateHrRequests_reusedId_idsCollide() throws Exception {
        int count = 3;

        BulkResponse response = mock(BulkResponse.class);
        when(response.errors()).thenReturn(false);
        when(client.bulk(any(BulkRequest.class))).thenReturn(response);

        // Both uploads reuse the same dataId ("hr") – the second batch's docs land on the first batch's _ids.
        elasticService.storeDataPoints(List.of(hrDataPoint("hr", count)), routingInfo);
        elasticService.storeDataPoints(List.of(hrDataPoint("hr", count)), routingInfo);

        ArgumentCaptor<BulkRequest> captor = ArgumentCaptor.forClass(BulkRequest.class);
        verify(client, times(2)).bulk(captor.capture());

        List<String> allElasticIds = captor.getAllValues().stream()
                .flatMap(r -> r.operations().stream())
                .map(op -> op.index().id())
                .toList();
        // Both requests emit count operations, but the second reuses the first's _ids: only `count` are distinct.
        assertThat(allElasticIds).hasSize(count * 2);
        assertThat(Set.copyOf(allElasticIds)).hasSize(count); // second batch would overwrite the first in Elastic
    }
}