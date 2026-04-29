package io.redlink.more.data.service.observations.limesurvey;

import io.redlink.more.data.model.DataPoint;
import io.redlink.more.data.model.Observation;
import io.redlink.more.data.model.RoutingInfo;
import io.redlink.more.data.service.ElasticService;
import io.redlink.more.data.service.StudyService;
import io.redlink.more.data.service.observations.ObservationComponent;
import io.redlink.more.data.util.DateTimeUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
public class LimeSurveyComponent implements ObservationComponent {
    private static final Logger LOG = LoggerFactory.getLogger(LimeSurveyComponent.class);
    private static final String LIME_SURVEY_ID_KEY = "limeSurveyId";
    private static final String LIME_SURVEY_TOKEN_KEY = "token";
    private static final String LIME_SURVEY_URL_KEY = "limeUrl";
    private static final String LIME_SAVE_ID = "savedId";
    private static final String LIME_SAVE_ID_ALT = "savedid";
    private static final String LIME_SAVE_ID_SHORT = "saveId";
    private static final String LIME_RESPONSE_SURVEY_ID = "surveyId";

    private final LimeSurveyRequestService limeSurveyRequestService;
    private final ElasticService elasticService;
    private final StudyService studyService;

    public LimeSurveyComponent(LimeSurveyRequestService limeSurveyRequestService, ElasticService elasticService, StudyService studyService) {
        this.limeSurveyRequestService = limeSurveyRequestService;
        this.elasticService = elasticService;
        this.studyService = studyService;
    }

    @Override
    public String getObservationType() {
        return "lime-survey-observation";
    }

    @Override
    public Optional<String> produceUrl(Observation observation, RoutingInfo routingInfo, Instant scheduleStart, Instant scheduleEnd) {
        return generateLimeSurveyUrl(observation);
    }

    @Override
    public Optional<Pair<RoutingInfo, Integer>> processCallback(RoutingInfo routingInfo, Observation observation, Map<String, String> parameters) {
        String token = getParameter(parameters, LIME_SURVEY_TOKEN_KEY, LIME_SURVEY_ID_KEY);
        String savedId = getParameter(parameters, LIME_SAVE_ID, LIME_SAVE_ID_ALT, LIME_SAVE_ID_SHORT);
        String surveyIdParam = getParameter(parameters, LIME_RESPONSE_SURVEY_ID);

        if (token == null || savedId == null || surveyIdParam == null) {
            LOG.warn("missing required parameter for callback with routingInfo: {}; observation: {}; parameters: {}", routingInfo, observation.observationId(), parameters);
            throw new IllegalArgumentException("Necessary parameter not provided! Please provide all of these: token, savedId, surveyId!");
        }
        Integer saveId = Integer.parseInt(savedId);
        Integer surveyId = Integer.parseInt(surveyIdParam);
        if (storeAnswer(surveyId, saveId, token, routingInfo, Integer.toString(observation.observationId()))) {
            return Optional.of(Pair.of(routingInfo, observation.observationId()));
        }
        return Optional.empty();
    }

    private String getParameter(Map<String, String> parameters, String... keys) {
        for (String key : keys) {
            if (parameters.containsKey(key)) {
                return parameters.get(key);
            }
        }
        for (String key : keys) {
            for (Map.Entry<String, String> entry : parameters.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(key)) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    private Optional<String> generateLimeSurveyUrl(Observation observation) {
        var props = observation.properties();
        if (!(props instanceof Map<?, ?> properties)) {
            return Optional.empty();
        }

        String surveyId = asString(properties.get(LIME_SURVEY_ID_KEY));
        String surveyToken = asString(properties.get(LIME_SURVEY_TOKEN_KEY));
        String surveyUrl = limeSurveyRequestService.getBaseUrl()
                .orElse(asString(properties.get(LIME_SURVEY_URL_KEY)));

        return formatLimeSurveyDeepLink(surveyId, surveyToken, surveyUrl);
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Optional<String> formatLimeSurveyDeepLink(String surveyId, String surveyToken, String surveyUrl) {
        if (surveyId == null || surveyId.isBlank() || surveyToken == null || surveyToken.isBlank() || surveyUrl == null || surveyUrl.isBlank()) {
            throw new IllegalStateException("Lime survey token, ID or url is empty!");
        }

        String base = surveyUrl.startsWith("http") ? surveyUrl : "https://" + surveyUrl;
        String normalizedBase = base.endsWith("/") ? base : base + "/";
        String encodedToken = URLEncoder.encode(surveyToken, StandardCharsets.UTF_8);

        try {
            URI baseUri = URI.create(normalizedBase);
            URI result = new URI(
                    baseUri.getScheme(),
                    baseUri.getAuthority(),
                    baseUri.getPath() + surveyId,
                    LIME_SURVEY_TOKEN_KEY + "=" + encodedToken,
                    null
            );
            return Optional.of(result.toString());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(
                    String.format("Could not create Uri for lime survey %s, with survey id %s", surveyUrl, surveyId),
                    e
            );
        }
    }

    private boolean storeAnswer(Integer surveyId, Integer saveId, String token, RoutingInfo routingInfo, String observationId) {
        Optional<Map<String, Object>> answerOpt = limeSurveyRequestService.getAnswer(token, surveyId, saveId);
        if (answerOpt.isPresent() && !answerOpt.get().isEmpty()) {

            Map<String, Object> answer = answerOpt.get();
            Object submitDateObj = answer.remove("submitdate");
            Instant dateSubmitted = Optional
                    .ofNullable(submitDateObj)
                    .map(Object::toString)
                    .map(obj -> DateTimeUtils.parseInstantWithOffset(obj, ZoneOffset.UTC))
                    .orElse(Instant.now());
            DataPoint dataPoint = new DataPoint(
                    UUID.randomUUID().toString(),
                    observationId,
                    getObservationType(),
                    getObservationType(),
                    Instant.now(),
                    dateSubmitted,
                    answer
            );

            try {
                elasticService.storeDataPoints(List.of(dataPoint), routingInfo);
                LOG.info("Stored LimeSurvey answer for survey {}, token {}, observation {}", surveyId, token, observationId);
                return true;
            } catch (IOException e) {
                LOG.error("Error storing LimeSurvey answers: {}", e.toString());
            }
        } else {
            LOG.warn("Could not fetch answer for LimeSurvey survey {}, token {}, savedId {}", surveyId, token, saveId);
        }
        return false;
    }
}
