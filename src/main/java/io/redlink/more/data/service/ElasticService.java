/*
 * Copyright LBI-DHP and/or licensed to LBI-DHP under one or more
 * contributor license agreements (LBI-DHP: Ludwig Boltzmann Institute
 * for Digital Health and Prevention -- A research institute of the
 * Ludwig Boltzmann Gesellschaft, Oesterreichische Vereinigung zur
 * Foerderung der wissenschaftlichen Forschung).
 * Licensed under the Elastic License 2.0.
 */
package io.redlink.more.data.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.DeleteByQueryRequest;
import co.elastic.clients.elasticsearch.core.DeleteByQueryResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import io.redlink.more.data.api.StorageService;
import io.redlink.more.data.elastic.model.ElasticDataPoint;
import io.redlink.more.data.model.DataPoint;
import io.redlink.more.data.model.RoutingInfo;
import io.redlink.more.data.util.ElasticUtils;
import java.util.ArrayList;
import org.apache.commons.lang3.Range;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Set;

@Service
public class ElasticService implements StorageService {
    private static final Logger LOG = LoggerFactory.getLogger(ElasticService.class);

    // Max number of bulk operations sent to Elasticsearch in a single _bulk request.
    // Exploded polar360 data points can produce tens of thousands of operations from a
    // single upload; sending them all at once exceeds http.max_content_length (413) and
    // indexing-pressure limits (429). Flushing in fixed-size chunks keeps each request small.
    private static final int BULK_CHUNK_SIZE = 2000;

    private final ElasticsearchClient client;

    ElasticService(ElasticsearchClient elasticsearchClient) {
        this.client = elasticsearchClient;
    }

    private String getElasticIndexName(RoutingInfo routingInfo) {
        return "study_" + routingInfo.studyId();
    }


    public List<String> storeDataPoints(final List<DataPoint> dataBulk, final RoutingInfo routingInfo) throws IOException {
        final String indexName = getElasticIndexName(routingInfo);
        final String uidPrefix = generateUidPrefix(routingInfo);
        boolean is_exploded = false;
        final List<String> exploded_returnId = new ArrayList<>();
        // Collect every operation first, then flush to Elasticsearch in fixed-size chunks
        // (see BULK_CHUNK_SIZE) rather than building one unbounded _bulk request.
        final List<BulkOperation> operations = new ArrayList<>();

        for (DataPoint dataPoint : dataBulk) {
            final var uid = uidPrefix + dataPoint.datapointId();
            //TODO create transformer so its cleaner mapping solution bulk request operations fails
            if (dataPoint.data().keySet().stream().anyMatch(key -> key.toLowerCase().contains("polar360"))) {
                is_exploded = true;
                final List<ElasticDataPoint> elasticItems = ElasticDataPoint.explode_toElastic(dataPoint, routingInfo);
                if (elasticItems.isEmpty()) {
                    LOG.warn("polar360 data point {} produced no exploded items, skipping", dataPoint.datapointId());
                    continue;
                }
                exploded_returnId.add(dataPoint.datapointId());
                int counter = 0;
                for (ElasticDataPoint e : elasticItems) {
                    final String explodedId = uid + "-" + counter++;
                    operations.add(BulkOperation.of(op -> op
                            .index(idx -> idx
                                    .index(indexName)
                                    .id(explodedId)
                                    .document(e)
                            )
                    ));
                }
            } else {
                final ElasticDataPoint elasticDoc = ElasticDataPoint.toElastic(dataPoint, routingInfo);
                operations.add(BulkOperation.of(op -> op
                        .index(idx -> idx
                                .index(indexName)
                                .id(uid)
                                .document(elasticDoc)
                        )
                ));
            }
        }

        if (exploded_returnId.isEmpty() && is_exploded) {
            LOG.warn("All polar360 data points produced no exploded items, nothing to store");
            return List.of();
        }
        if (operations.isEmpty()) {
            return List.of();
        }

        final List<String> storedIds = new ArrayList<>();
        try {
            for (int from = 0; from < operations.size(); from += BULK_CHUNK_SIZE) {
                final int to = Math.min(from + BULK_CHUNK_SIZE, operations.size());
                final List<BulkOperation> chunk = operations.subList(from, to);

                final BulkRequest bulkRequest = new BulkRequest.Builder()
                        .index(indexName)
                        .operations(chunk)
                        .build();

                LOG.debug("Sending bulk chunk [{}-{}) of {} operations to {}", from, to, operations.size(), indexName);
                final BulkResponse result = client.bulk(bulkRequest);

                // Log errors, if any
                if (LOG.isErrorEnabled() && result.errors()) {
                    LOG.error("Bulk chunk [{}-{}) had errors", from, to);
                    for (BulkResponseItem item : result.items()) {
                        if (item.error() != null) {
                            LOG.error("{}: {}", item.id(), item.error().reason());
                        }
                    }
                }

                if (!is_exploded) {
                    result.items().stream()
                            .filter(i -> i.error() == null)
                            .map(BulkResponseItem::id)
                            .filter(StringUtils::isNotBlank)
                            .map(i -> i.substring(uidPrefix.length()))
                            .forEach(storedIds::add);
                }
            }
        } catch (IOException | ElasticsearchException e) {
            LOG.warn("Error when sending data bulk to elastic index. Error message: {}", e.toString());
            throw e;
        }

        return is_exploded ? exploded_returnId : storedIds;
    }

    public Long deleteDataPointsInTimeRanges(RoutingInfo routingInfo, String dataType, Set<Range<Instant>> effectiveDateTimes) throws IOException {
        if (effectiveDateTimes.isEmpty() || dataType == null || dataType.isBlank()) {
            return 0L;
        }

        // Each Range becomes a should-clause in the bool query.
        // Chunk to avoid hitting indices.query.bool.max_clause_count.
        final int maxRangesPerRequest = 200;

        long totalDeleted = 0L;
        final List<Range<Instant>> rangesList = List.copyOf(effectiveDateTimes);

        for (int from = 0; from < rangesList.size(); from += maxRangesPerRequest) {
            int to = Math.min(from + maxRangesPerRequest, rangesList.size());
            Set<Range<Instant>> chunk = new java.util.HashSet<>(rangesList.subList(from, to));

            DeleteByQueryRequest request = new DeleteByQueryRequest.Builder()
                    .index(ElasticUtils.getStudyIdString(routingInfo.studyId()))
                    .query(ElasticUtils.getDeleteDataPointsRangeFilter(
                            routingInfo.studyId(),
                            routingInfo.participantId(),
                            ElasticUtils.Constants.EFFECTIVE_TIME_FRAME_FIELD,
                            chunk,
                            dataType
                    ))
                    .build();

            try {
                DeleteByQueryResponse response = client.deleteByQuery(request);
                totalDeleted += response.deleted() == null ? 0L : response.deleted();
            } catch (ElasticsearchException e) {
                // preserve your existing behavior for handled deletion exceptions
                totalDeleted += ElasticUtils.handleDeletionException(e, routingInfo.studyId());
            }
        }

        return totalDeleted;
    }


    public long deleteDataPoints(
            RoutingInfo routingInfo,
            String dataType,
            String key,
            Set<?> filteredByValues
    ) throws IOException {
        if (filteredByValues.isEmpty() || key == null || dataType == null || dataType.isBlank()) {
            return 0L;
        }

        Set<FieldValue> fieldValues = filteredByValues.stream()
                .map(v -> {
                    if (v instanceof Instant i) {
                        return FieldValue.of(i.toString());
                    }
                    return FieldValue.of(v.toString());
                })
                .collect(java.util.stream.Collectors.toSet());

        DeleteByQueryRequest request = new DeleteByQueryRequest.Builder()
                .index(ElasticUtils.getStudyIdString(routingInfo.studyId()))
                .query(ElasticUtils.getDeleteDataPointsFilter(
                                routingInfo.studyId(),
                                routingInfo.participantId(),
                                key,
                                fieldValues,
                                dataType
                        )
                )
                .build();

        try {
            DeleteByQueryResponse response = client.deleteByQuery(request);
            return response.deleted() == null ? 0L : response.deleted();
        } catch (ElasticsearchException e) {
            return ElasticUtils.handleDeletionException(e, routingInfo.studyId());
        }
    }


    private String generateUidPrefix(RoutingInfo routingInfo) {
        return String.format("%s_%s_", routingInfo.studyId(), routingInfo.participantId());
    }
}
