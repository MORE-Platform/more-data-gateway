package io.redlink.more.data.util;

import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.json.JsonData;
import org.apache.commons.lang3.Range;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Set;

public class ElasticUtils {
    private static final Logger LOG = LoggerFactory.getLogger(ElasticUtils.class);

    public static class Constants {
        public static final String GARMIN_SUMMARY_ID_KEY = "summary_id";
        public static final String GARMIN_SUMMARY_DATA_FIELD = "data_" + GARMIN_SUMMARY_ID_KEY;
        public static final String GARMIN_SUMMARY_KEYWORD_FIELD = GARMIN_SUMMARY_DATA_FIELD + ".keyword";
        public static final String STUDY_FIELD = "study_";
        public static final String STUDY_KEYWORD_FIELD = "study_id.keyword";
        public static final String PARTICIPANT_FIELD = "participant_";
        public static final String PARTICIPANT_KEYWORD_FIELD = "participant_id.keyword";
        public static final String EFFECTIVE_TIME_FRAME_FIELD = "effective_time_frame";
    }

    public static Query getStudyIdFilter(Long studyId) {
        return Query.of(q -> q.term(t -> t
                .field(Constants.STUDY_KEYWORD_FIELD)
                .value(getStudyIdString(studyId))));
    }

    public static Query getParticipantIdFilter(Integer participantId) {
        return Query.of(q -> q.term(t -> t
                .field(Constants.PARTICIPANT_KEYWORD_FIELD)
                .value(getParticipantIdString(participantId))));
    }

    public static Query getFieldValuesFilter(String field, Set<FieldValue> values) {
        return Query.of(q -> q.terms(t -> t
                .field(field)
                .terms(v -> v.value(new ArrayList<>(values)))));
    }

    public static Query getDeleteDataPointsFilter(
            Long studyId,
            Integer participantId,
            String field,
            Set<FieldValue> values,
            String dataType
    ) {
        if (dataType == null || dataType.isBlank()) {
            throw new IllegalArgumentException("Data type cannot be null or blank");
        }
        return Query.of(q -> q
                .bool(b -> b
                        .filter(getStudyIdFilter(studyId))
                        .filter(getParticipantIdFilter(participantId))
                        .filter(getFieldValuesFilter(field, values))
                        .filter(f -> f.term(t -> t.field("data_type.keyword").value(dataType)))
                )
        );
    }

    public static Query getDeleteDataPointsRangeFilter(
            Long studyId,
            Integer participantId,
            String field,
            Set<Range<Instant>> ranges,
            String dataType
    ) {
        if (dataType == null || dataType.isBlank()) {
            throw new IllegalArgumentException("Data type cannot be null or blank");
        }
        return Query.of(q -> q
                .bool(b -> b
                        .filter(getStudyIdFilter(studyId))
                        .filter(getParticipantIdFilter(participantId))
                        .filter(f -> f.bool(sb -> sb
                                .minimumShouldMatch("1")
                                .should(ranges.stream()
                                        .map(range -> Query.of(rq -> rq.range(r -> r
                                                .field(field)
                                                .gte(JsonData.of(range.getMinimum().toString()))
                                                .lte(JsonData.of(range.getMaximum().toString()))
                                        )))
                                        .toList()
                                )
                        ))
                        .filter(f -> f.term(t -> t.field("data_type.keyword").value(dataType)))
                )
        );
    }

    public static String getParticipantIdString(Integer participantId) {
        return Constants.PARTICIPANT_FIELD + participantId;
    }

    public static String getStudyIdString(Long id) {
        return Constants.STUDY_FIELD + id;
    }

    public static long handleDeletionException(Exception e, Long studyId) {
        if (e instanceof ElasticsearchException ee) {
            if ("index_not_found_exception".equals(ee.error().type())) {
                LOG.debug("Index {} does not exist, nothing to delete", ElasticUtils.getStudyIdString(studyId));
                return 0L;
            }
            LOG.warn("Error when deleting data points from elastic index. Error message: ", e);
            return 0L;
        }
        throw new RuntimeException("Unexpected exception when deleting data points from elastic index", e);
    }
}
