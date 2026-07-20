/*
 * Copyright LBI-DHP and/or licensed to LBI-DHP under one or more
 * contributor license agreements (LBI-DHP: Ludwig Boltzmann Institute
 * for Digital Health and Prevention -- A research institute of the
 * Ludwig Boltzmann Gesellschaft, Österreichische Vereinigung zur
 * Förderung der wissenschaftlichen Forschung).
 * Licensed under the Elastic License 2.0.
 */
package io.redlink.more.data.service.observations.limesurvey;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.redlink.more.data.limesurvey.client.LimeSurveyRcApi;
import io.redlink.more.data.limesurvey.model.LimeSurveyMethod;
import io.redlink.more.data.limesurvey.model.LimeSurveyObjectResponse;
import io.redlink.more.data.limesurvey.model.LimeSurveyRequest;
import io.redlink.more.data.service.observations.limesurvey.config.LimeSurveyProperties;
import io.redlink.more.data.service.observations.limesurvey.model.ParticipantData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
public class LimeSurveyRequestService {
    private static final String LIME_NULL_DATE = "1980-01-01 00:00:00";
    private static final DateTimeFormatter LIME_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter OFFSET_DATE_TIME_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final ObjectMapper mapper = new ObjectMapper();
    private static final Logger LOGGER = LoggerFactory.getLogger(LimeSurveyRequestService.class);

    private final LimeSurveyRcApi limeSurveyRcApi;
    private final LimeSurveyProperties properties;

    public LimeSurveyRequestService(LimeSurveyRcApi limeSurveyRcApi, LimeSurveyProperties properties) {
        this.limeSurveyRcApi = limeSurveyRcApi;
        this.properties = properties;
    }

    private LimeSurveyRequest createRequest(LimeSurveyMethod method, Object... params) {
        return new LimeSurveyRequest()
                .method(method)
                .params(List.of(params))
                .id(1)
                .jsonrpc(LimeSurveyRequest.JsonrpcEnum._2_0);
    }

    public Optional<String> getBaseUrl() {
        return Optional.ofNullable(properties.getBaseUrl());
    }

    public String getLanguage(String surveyId, String sessionKey) {
        LimeSurveyObjectResponse response = limeSurveyRcApi.callMethod(
                createRequest(LimeSurveyMethod.GET_LANGUAGE_PROPERTIES, sessionKey, surveyId, List.of("surveyls_language"))
        );

        if (response == null) {
            throw new RuntimeException("Error reading language for survey " + surveyId + ": Response is null");
        }
        if (response.getError() != null && !response.getError().isBlank()) {
            throw new RuntimeException("Error reading language for survey " + surveyId + ": " + response.getError());
        }

        Object result = response.getResult();
        if (result instanceof Map<?, ?> resultMap) {
            Object status = resultMap.get("status");
            if (status != null) {
                throw new RuntimeException("Error reading language for survey " + surveyId + ": " + status);
            }
            Object lang = resultMap.get("surveyls_language");
            if (lang != null) {
                return String.valueOf(lang);
            }
        }

        throw new IllegalStateException("Missing survey language for survey " + surveyId + " (result=" + result + ")");
    }

    private String getSessionKey() {
        try {
            var response = limeSurveyRcApi.callMethod(createRequest(LimeSurveyMethod.GET_SESSION_KEY, properties.getUsername(), properties.getPassword()));
            if (response == null) {
                throw new RuntimeException("Error retrieving session key: Response is null");
            }
            if (response.getError() != null && !response.getError().isBlank()) {
                throw new RuntimeException("Error retrieving session key: " + response.getError());
            }

            if (response.getResult() == null) {
                throw new RuntimeException("LimeSurvey remote control returned no session key response.");
            }

            Object result = response.getResult();
            if (result instanceof Map<?, ?> resultMap) {
                Object status = resultMap.get("status");
                if (status != null) {
                    throw new RuntimeException("Error retrieving session key from LimeSurvey: " + status);
                }
            }

            if (!(result instanceof String key) || key.trim().isBlank()) {
                LOGGER.error("LimeSurvey remote control returned an invalid session key payload. remoteUrl={}, rawResponse={}", properties.getBaseUrl(), response);
                throw new RuntimeException("LimeSurvey returned an empty or invalid session key.");
            }
            if (key.contains("Invalid user name or password")) {
                throw new RuntimeException("Not possible to get session key for Limesurvey because of invalid credentials.");
            } else if (key.contains("You have exceeded the number of maximum login attempts. Please wait 10 minutes before trying again")) {
                throw new RuntimeException("Too many login attempts for Limesurvey. Try again in 10 minutes.");
            }


            return key.trim();
        } catch (RestClientException e) {
            LOGGER.error("Timeout while getting session key from Limesurvey remote control at {}", properties.getBaseUrl(), e);
            throw new RuntimeException(e);
        }
    }

    private void releaseSessionKey(String sessionKey) {
        if (sessionKey == null || sessionKey.isBlank()) {
            return;
        }
        try {
            limeSurveyRcApi.callMethod(createRequest(LimeSurveyMethod.RELEASE_SESSION_KEY, sessionKey));

        } catch (RestClientException e) {
            LOGGER.error("Error releasing session key for Limesurvey remote control", e);
            throw new RuntimeException(e);
        }
    }

    private void releaseSessionKeyQuietly(String sessionKey) {
        if (sessionKey == null || sessionKey.isBlank()) {
            return;
        }
        try {
            releaseSessionKey(sessionKey);
        } catch (RuntimeException e) {
            LOGGER.warn("Could not release session key cleanly", e);
        }
    }

    public Optional<ParticipantData> getParticipant(String token, int surveyId) {
        if (token == null || token.isBlank() || surveyId <= 0) {
            LOGGER.warn("Invalid participant query parameters: surveyId={}, token={}", surveyId, token);
            return Optional.empty();
        }
        String sessionKey = null;

        try {
            sessionKey = getSessionKey();
            var response = limeSurveyRcApi.callMethod(
                    createRequest(
                            LimeSurveyMethod.GET_PARTICIPANT_PROPERTIES,
                            sessionKey,
                            surveyId,
                            Map.of("token", token),
                            List.of("tid", "token", "participant_info", "firstname", "lastname", "email")
                    )
            );

            if (response == null) {
                LOGGER.warn("LimeSurvey returned null response for get_participant_properties (surveyId={})", surveyId);
                return Optional.empty();
            }
            if (response.getError() != null && !response.getError().isBlank()) {
                LOGGER.warn("LimeSurvey returned error for get_participant_properties (surveyId={}): {}", surveyId, response.getError());
                return Optional.empty();
            }
            if (response.getResult() == null) {
                LOGGER.warn("LimeSurvey returned null result for get_participant_properties (surveyId={})", surveyId);
                return Optional.empty();
            }

            Object result = response.getResult();
            if (result instanceof Map<?, ?> resultMap && resultMap.get("status") != null) {
                LOGGER.warn("LimeSurvey returned status for get_participant_properties (surveyId={}): {}", surveyId, resultMap.get("status"));
                return Optional.empty();
            }

            return Optional.of(mapper.convertValue(result, ParticipantData.class));
        } catch (IllegalArgumentException e) {
            LOGGER.error("Could not map LimeSurvey participant data for surveyId={} and token={}", surveyId, token, e);
            return Optional.empty();
        } catch (RuntimeException e) {
            LOGGER.error("Could not get participant from LimeSurvey remote control", e);
            return Optional.empty();
        } finally {
            releaseSessionKeyQuietly(sessionKey);
        }
    }

    public Optional<Map<String, Object>> getAnswer(String token, int surveyId, int savedId) {
        return getAnswer(token, surveyId, savedId, "code", "short");
    }

    public Optional<Map<String, Object>> getAnswerPlaintext(String token, int surveyId, int savedId) {
        return getAnswer(token, surveyId, savedId, "full", "long");
    }

    private Optional<Map<String, Object>> getAnswer(String token, int surveyId, int savedId, String headingType, String responseType) {
        if (token == null || token.isBlank() || surveyId <= 0 || savedId <= 0) {
            LOGGER.warn("Invalid answer query parameters: surveyId={}, savedId={}", surveyId, savedId);
            return Optional.empty();
        }

        String sessionKey = null;
        try {
            sessionKey = getSessionKey();
            String lang = getLanguage(String.valueOf(surveyId), sessionKey);
            var apiResponse = limeSurveyRcApi.callMethod(createRequest(LimeSurveyMethod.EXPORT_RESPONSES_BY_TOKEN, sessionKey, surveyId, "json", token, lang, "all", headingType, responseType));

            if (apiResponse == null) {
                LOGGER.warn("LimeSurvey returned null response for export_responses_by_token (surveyId={})", surveyId);
                return Optional.empty();
            }
            if (apiResponse.getError() != null && !apiResponse.getError().isBlank()) {
                LOGGER.warn("LimeSurvey returned error for export_responses_by_token (surveyId={}): {}", surveyId, apiResponse.getError());
                return Optional.empty();
            }

            if (apiResponse.getResult() == null) {
                LOGGER.warn("LimeSurvey returned null result for export_responses_by_token (surveyId={})", surveyId);
                return Optional.empty();
            }
            JsonNode result = mapper.readTree(Base64.getDecoder().decode(apiResponse.getResult().toString()));
            JsonNode responsesNode = result.path("responses");
            if (!responsesNode.isArray()) {
                LOGGER.warn("Limesurvey returned no responses (surveyId={}): {}", surveyId, result.asText());
                return Optional.empty();
            }

            Iterator<JsonNode> responses = responsesNode.elements();
            while (responses.hasNext()) {
                JsonNode response = responses.next();
                if (response == null || !response.isObject()) {
                    continue;
                }

                Map<String, Object> answer = mapper.convertValue(response, Map.class);
                //NOTE: Do not store the survey token
                answer.remove("token");
                answer.values().removeIf(obj -> Objects.isNull(obj) || obj.equals(token));

                normalizeDateFields(answer);

                Object responseId = answer.get("Response ID");
                Object id = answer.get("id");
                boolean matchesSavedId = Objects.equals(String.valueOf(savedId), String.valueOf(responseId))
                        || Objects.equals(String.valueOf(savedId), String.valueOf(id));

                if (matchesSavedId || responsesNode.size() == 1) {
                    return Optional.of(new HashMap<>(answer));
                }
            }
            return Optional.empty();
        } catch (IllegalArgumentException e) {
            LOGGER.error("Could not decode LimeSurvey response payload for survey {} and savedId {}", surveyId, savedId, e);
            return Optional.empty();
        } catch (RestClientException e) {
            LOGGER.error("Error reading results for {}", surveyId, e);
            return Optional.empty();
        } catch (IOException e) {
            LOGGER.error("Could not decode queried result data for survey {}, saveId {}", surveyId, savedId, e);
            throw new RuntimeException(e);
        } finally {
            releaseSessionKeyQuietly(sessionKey);
        }
    }

    private void normalizeDateFields(Map<String, Object> answer) {
        answer.replaceAll((key, value) -> {
            if (value == null || !isDateField(key)) {
                return value;
            }

            String dateValue = String.valueOf(value);
            if (LIME_NULL_DATE.equals(dateValue)) {
                return LocalDateTime.now(ZoneOffset.UTC)
                        .atOffset(ZoneOffset.UTC)
                        .format(OFFSET_DATE_TIME_FORMATTER);
            }

            var normalized = normalizeDateTimeString(dateValue);
            if (normalized.isPresent()) {
                return normalized.get();
            }
            return value;
        });
    }

    private boolean isDateField(String key) {
        if (key == null) {
            return false;
        }
        String normalizedKey = key.toLowerCase();
        return normalizedKey.contains("date") || normalizedKey.contains("datestamp") || normalizedKey.contains("timestamp");
    }

    private Optional<String> normalizeDateTimeString(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(
                    LocalDateTime.parse(value, LIME_DATE_FORMATTER)
                            .atOffset(ZoneOffset.UTC)
                            .format(OFFSET_DATE_TIME_FORMATTER)
            );
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

}
