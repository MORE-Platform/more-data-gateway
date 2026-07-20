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
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import io.redlink.more.data.api.StorageService;
import io.redlink.more.data.elastic.model.ElasticDataPoint;
import io.redlink.more.data.model.DataPoint;
import io.redlink.more.data.model.RoutingInfo;
import io.redlink.more.data.util.ElasticUtils;
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

        try {
            final BulkRequest.Builder br = new BulkRequest.Builder()
                    .index(indexName);

            for (DataPoint dataPoint : dataBulk) {
                final var uid = uidPrefix + dataPoint.datapointId();
                final ElasticDataPoint elasticDoc = ElasticDataPoint.toElastic(dataPoint, routingInfo);
                br.operations(op -> op
                        .index(idx -> idx
                                .index(indexName)
                                .id(uid)
                                .document(elasticDoc)
                        )
                );
            }

            LOG.debug("Sending {} data-points to {}", dataBulk.size(), indexName);
            final BulkResponse result = client.bulk(br.build());

            // Log errors, if any
            if (LOG.isErrorEnabled() && result.errors()) {
                LOG.error("Bulk had errors");
                for (BulkResponseItem item : result.items()) {
                    if (item.error() != null) {
                        LOG.error("{}: {}", item.id(), item.error().reason());
                    }
                }
            }

            return result.items().stream()
                    .filter(i -> i.error() == null)
                    .map(BulkResponseItem::id)
                    .filter(StringUtils::isNotBlank)
                    .map(i -> i.substring(uidPrefix.length()))
                    .toList();
        } catch (IOException | ElasticsearchException e) {
            LOG.warn("Error when sending data bulk to elastic index. Error message: {}", e.toString());
            throw e;
        }
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
