package io.redlink.more.data.service.observations.limesurvey;

import io.redlink.more.data.model.CallbackResult;
import io.redlink.more.data.model.DataPoint;
import io.redlink.more.data.model.Observation;
import io.redlink.more.data.model.RoutingInfo;
import io.redlink.more.data.model.RoutingInfoWithObservation;
import io.redlink.more.data.service.ElasticService;
import io.redlink.more.data.service.StudyService;
import io.redlink.more.data.service.observations.ObservationComponent;
import io.redlink.more.data.service.observations.limesurvey.model.ParticipantData;
import io.redlink.more.data.util.DateTimeUtils;
import io.redlink.more.data.util.MapperUtils;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class LimeSurveyComponent implements ObservationComponent {
    private static final String LIME_SURVEY_USER_TEMPLATE = "study_%s-observation_%s-participant_%s";

    private static final Logger LOG = LoggerFactory.getLogger(LimeSurveyComponent.class);
    private static final String LIME_SURVEY_ID_KEY = "limeSurveyId";
    private static final String LIME_SURVEY_TOKEN_KEY = "token";
    private static final String LIME_SURVEY_URL_KEY = "limeUrl";

    private static final String[] LIME_SURVEY_ID_RESPONSE_KEYS = {"surveyId", "survey-id", "surveyid", "sid"};
    private static final String[] LIME_SURVEY_SAVE_ID_KEYS = {"savedId", "savedid", "saveId"};
    private static final String[] STUDY_ID_KEYS = {"studyId", "study-id", "studyid"};

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
    public boolean necessaryCallbackParameters(Map<String, String> parameters) {
        return MapperUtils.containsParameter(parameters, STUDY_ID_KEYS)
                && MapperUtils.containsParameter(parameters, LIME_SURVEY_TOKEN_KEY)
                && MapperUtils.containsParameter(parameters, LIME_SURVEY_ID_RESPONSE_KEYS);
    }

    @Override
    public Optional<CallbackResult> processCallback(Map<String, String> parameters) {
        Optional<Long> studyIdParam = Optional.ofNullable(MapperUtils.getParameter(parameters, STUDY_ID_KEYS))
                .map(Long::parseLong);
        String token = MapperUtils.getParameter(parameters, LIME_SURVEY_TOKEN_KEY);
        int savedId = parseSavedId(MapperUtils.getParameter(parameters, LIME_SURVEY_SAVE_ID_KEYS));
        Optional<Integer> surveyId = Optional.ofNullable(MapperUtils.getParameter(parameters, LIME_SURVEY_ID_RESPONSE_KEYS))
                .map(Integer::parseInt);

        if (studyIdParam.isEmpty() || token == null || surveyId.isEmpty()) {
            LOG.warn("Missing parameters for LimeSurvey Component: studyId={}, savedId={}, surveyId={}", studyIdParam, savedId, surveyId);
            throw new IllegalArgumentException("Necessary parameter not provided! Please provide all of these: studyID, observationId, token!");
        }

        RoutingInfoWithObservation routingInfoAndObservationId = studyService.getRoutingInfoByToken(studyIdParam.get(), token)
                .or(() -> limeSurveyRequestService.getParticipant(token, surveyId.get())
                        .flatMap(this::lsParticipantToRoutingInfo))
                .orElseThrow(() -> {
                    LOG.warn("Could not find participant for LimeSurvey Component: studyId={}, savedId={}, surveyId={}", studyIdParam, savedId, surveyId);
                    return new IllegalArgumentException("Could not find participant for LimeSurvey Component for provided parameters");
                });

        RoutingInfo routingInfo = routingInfoAndObservationId.routingInfo();
        Integer resolvedObservationId = routingInfoAndObservationId.observationId();

        if (routingInfo == null || resolvedObservationId == null) {
            LOG.warn("Could not find RoutingInfo for LimeSurvey Component: studyId={}, savedId={}, surveyId={}", studyIdParam, savedId, surveyId);
            throw new IllegalArgumentException("Could not find RoutingInfo for LimeSurvey Component for provided parameters");
        }

        if (storeAnswer(surveyId.get(), savedId, token, routingInfo, resolvedObservationId.toString())) {
            LOG.debug("Stored LimeSurvey answer for survey {}, observation {}", surveyId.get(), resolvedObservationId);
            return Optional.of(new CallbackResult(routingInfo, resolvedObservationId));
        }
        LOG.warn("Failed to store LimeSurvey answer for survey {}, savedId {}, observation {}", surveyId.get(), savedId, resolvedObservationId);
        return Optional.empty();
    }

    private Optional<String> generateLimeSurveyUrl(Observation observation) {
        var props = observation.properties();
        if (!(props instanceof Map<?, ?> properties)) {
            LOG.warn("Observation properties are not a Map! {}", props);
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

    private boolean storeAnswer(Integer surveyId, int saveId, String token, RoutingInfo routingInfo, String observationId) {
        Optional<Map<String, Object>> answerOpt = limeSurveyRequestService.getAnswer(token, surveyId, saveId);
        if (answerOpt.isEmpty() || answerOpt.get().isEmpty()) {
            LOG.warn("Could not fetch answer for LimeSurvey survey {}, savedId {}, routingInfo {}", surveyId, saveId, routingInfo);
            return false;
        }

        LOG.info("Received answer for LimeSurvey survey {}, routingInfo {}, savedId {}", surveyId, routingInfo, saveId);
        DataPoint dataPoint = toDataPoint(surveyId, saveId, answerOpt.get(), observationId);

        try {
            if (elasticService.storeDataPoints(List.of(dataPoint), routingInfo).isEmpty()) {
                LOG.error("Elastic rejected LimeSurvey answer for survey {}, savedId {}, observation {}", surveyId, saveId, observationId);
                return false;
            }
            LOG.info("Stored LimeSurvey answer for survey {}, observation {}", surveyId, observationId);
            return true;
        } catch (IOException e) {
            LOG.error("Error storing LimeSurvey answers: {}", e.toString());
        }
        return false;
    }

    /**
     * Re-collects every response LimeSurvey holds for this participant's survey and (re-)stores it.
     * Used by the operator-triggered resync; safe to repeat, because the datapoint ids are stable.
     *
     * @param properties the participant's observation properties, carrying the survey id and token
     * @return the number of answers stored, {@code 0} if there was nothing to store or LimeSurvey failed
     */
    public int resync(RoutingInfo routingInfo, Integer observationId, Map<String, Object> properties) {
        int surveyId;
        try {
            surveyId = Integer.parseInt(asString(properties.get(LIME_SURVEY_ID_KEY)));
        } catch (NumberFormatException | NullPointerException e) {
            LOG.warn("Observation {} of participant {} has no usable {}", observationId, routingInfo.participantId(), LIME_SURVEY_ID_KEY);
            return 0;
        }
        String token = asString(properties.get(LIME_SURVEY_TOKEN_KEY));
        if (token == null || token.isBlank()) {
            LOG.warn("Observation {} of participant {} has no LimeSurvey token", observationId, routingInfo.participantId());
            return 0;
        }

        List<Map<String, Object>> answers = limeSurveyRequestService.getAnswers(token, surveyId);
        if (answers.isEmpty()) {
            LOG.info("No LimeSurvey answers to resync for survey {}, observation {}, participant {}",
                    surveyId, observationId, routingInfo.participantId());
            return 0;
        }

        List<DataPoint> dataPoints = answers.stream()
                .map(answer -> toDataPoint(surveyId, 0, answer, observationId.toString()))
                .toList();

        try {
            int stored = elasticService.storeDataPoints(dataPoints, routingInfo).size();
            LOG.info("Resynced {} of {} LimeSurvey answers for survey {}, observation {}, participant {}",
                    stored, dataPoints.size(), surveyId, observationId, routingInfo.participantId());
            return stored;
        } catch (IOException e) {
            LOG.error("Error resyncing LimeSurvey answers for survey {}, observation {}: {}", surveyId, observationId, e.toString());
            return 0;
        }
    }

    private DataPoint toDataPoint(Integer surveyId, int saveId, Map<String, Object> answer, String observationId) {
        Object submitDateObj = answer.remove("submitdate");
        Instant dateSubmitted = Optional
                .ofNullable(submitDateObj)
                .map(Object::toString)
                .map(obj -> DateTimeUtils.parseInstantWithOffset(obj, ZoneOffset.UTC))
                .orElse(Instant.now());
        return new DataPoint(
                datapointId(surveyId, saveId, answer, observationId),
                observationId,
                null,
                getObservationType(),
                getObservationType(),
                Instant.now(),
                dateSubmitted,
                answer
        );
    }

    /**
     * A stable id per LimeSurvey response and observation, so re-processing a callback or re-syncing an answer
     * overwrites the existing Elastic document instead of adding a duplicate - while two observations pointing at
     * the same survey stay separate. Study and participant are already part of the Elastic document id
     * (see {@code ElasticService#generateUidPrefix}), so they are not repeated here.
     */
    private static String datapointId(Integer surveyId, int saveId, Map<String, Object> answer, String observationId) {
        long responseId = LimeSurveyRequestService.responseIdOf(answer);
        if (responseId != Long.MIN_VALUE) {
            return "limesurvey_" + observationId + "_" + surveyId + "_" + responseId;
        }
        if (saveId > 0) {
            return "limesurvey_" + observationId + "_" + surveyId + "_" + saveId;
        }
        return UUID.randomUUID().toString();
    }

    private static int parseSavedId(String savedId) {
        if (savedId == null || savedId.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(savedId.trim());
        } catch (NumberFormatException e) {
            LOG.warn("Ignoring unparseable LimeSurvey savedId `{}`", savedId);
            return 0;
        }
    }

    private Optional<RoutingInfoWithObservation> lsParticipantToRoutingInfo(ParticipantData participantData) {
        if (participantData == null || participantData.firstname() == null) {
            LOG.warn("Could not map participant data to RoutingInfo: {}", participantData);
            return Optional.empty();
        }

        String firstname = participantData.firstname();

        if (firstname.matches("\\d+")) {
            LOG.warn("Could not map LimeSurvey firstname to RoutingInfo as it conforms to the old format: {}", participantData);
            return Optional.empty();
        }

        String pattern = LIME_SURVEY_USER_TEMPLATE
                .replace("%s", "(\\d+)")
                .replace("study_", "^study_")
                .replace("-participant_", "-participant_") + "$";

        Matcher matcher = Pattern.compile(pattern).matcher(firstname);
        if (!matcher.matches()) {
            LOG.warn("Could not map LimeSurvey firstname to RoutingInfo: {}", firstname);
            return Optional.empty();
        }

        long studyId = Long.parseLong(matcher.group(1));
        int observationId = Integer.parseInt(matcher.group(2));
        int participantId = Integer.parseInt(matcher.group(3));

        if (studyId <= 0 || observationId <= 0 || participantId <= 0) {
            LOG.warn("Could not map LimeSurvey firstname to RoutingInfo: {}", firstname);
            return Optional.empty();
        }

        RoutingInfo routingInfo = studyService.getRoutingInfo(studyId, participantId)
                .orElse(null);

        if (routingInfo == null) {
            LOG.warn("Could not find RoutingInfo for LimeSurvey participant data: {}", participantData);
            return Optional.empty();
        }

        return Optional.of(new RoutingInfoWithObservation(routingInfo, observationId));
    }
}
