package io.redlink.more.data.service;


import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.DeleteByQueryRequest;
import co.elastic.clients.elasticsearch.core.DeleteByQueryResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import io.redlink.more.data.model.DataPoint;
import io.redlink.more.data.model.RoutingInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
}