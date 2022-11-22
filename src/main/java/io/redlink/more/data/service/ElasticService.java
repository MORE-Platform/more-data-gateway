package io.redlink.more.data.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import io.redlink.more.data.api.StorageService;
import io.redlink.more.data.elastic.model.ElasticDataPoint;
import io.redlink.more.data.model.DataPoint;
import io.redlink.more.data.model.RoutingInfo;
import java.io.IOException;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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


    public List<String> storeDataPoints(final List<DataPoint> dataBulk, final RoutingInfo routingInfo) {
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
            LOG.warn("Error when sending data bulk to elastic index. Error message: ", e);
            return List.of();
        }
    }

    private String generateUidPrefix(RoutingInfo routingInfo) {
        return String.format("%s_%s_", routingInfo.studyId(), routingInfo.participantId());
    }
}
