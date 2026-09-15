package io.redlink.more.data.service.observations.limesurvey;

import io.redlink.more.data.limesurvey.client.LimeSurveyRcApi;
import io.redlink.more.data.limesurvey.model.LimeSurveyMethod;
import io.redlink.more.data.limesurvey.model.LimeSurveyObjectResponse;
import io.redlink.more.data.limesurvey.model.LimeSurveyRequest;
import io.redlink.more.data.service.observations.limesurvey.config.LimeSurveyProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LimeSurveyRequestServiceTest {

    @Mock
    private LimeSurveyRcApi limeSurveyRcApi;

    @Mock
    private LimeSurveyProperties properties;

    private LimeSurveyRequestService limeSurveyRequestService;

    @BeforeEach
    void setUp() {
        when(properties.getUsername()).thenReturn("user");
        when(properties.getPassword()).thenReturn("pass");
        limeSurveyRequestService = new LimeSurveyRequestService(limeSurveyRcApi, properties);
    }

    @Test
    void testGetLanguageSuccess() {
        String sessionKey = "session123";
        String surveyId = "100";
        LimeSurveyObjectResponse response = new LimeSurveyObjectResponse();
        response.setResult(Map.of("surveyls_language", "en"));

        ArgumentCaptor<LimeSurveyRequest> requestCaptor = ArgumentCaptor.forClass(LimeSurveyRequest.class);
        when(limeSurveyRcApi.callMethod(requestCaptor.capture())).thenReturn(response);

        String lang = limeSurveyRequestService.getLanguage(surveyId, sessionKey);

        assertEquals("en", lang);
        LimeSurveyRequest request = requestCaptor.getValue();
        assertEquals(LimeSurveyMethod.GET_LANGUAGE_PROPERTIES, request.getMethod());
        assertEquals(1, request.getId());
        assertEquals(LimeSurveyRequest.JsonrpcEnum._2_0, request.getJsonrpc());
    }

    @Test
    void testGetLanguageError() {
        String sessionKey = "session123";
        String surveyId = "100";
        LimeSurveyObjectResponse response = new LimeSurveyObjectResponse();
        response.setError("Some error");

        when(limeSurveyRcApi.callMethod(any())).thenReturn(response);

        assertThrows(RuntimeException.class, () -> limeSurveyRequestService.getLanguage(surveyId, sessionKey));
    }

    @Test
    void testGetAnswerSuccess() throws Exception {
        String token = "token123";
        int surveyId = 100;
        int savedId = 50;
        String sessionKey = "session123";

        LimeSurveyObjectResponse sessionResponse = new LimeSurveyObjectResponse();
        sessionResponse.setResult(sessionKey);

        LimeSurveyObjectResponse langResponse = new LimeSurveyObjectResponse();
        langResponse.setResult(Map.of("surveyls_language", "en"));

        LimeSurveyObjectResponse exportResponse = new LimeSurveyObjectResponse();
        Map<String, Object> responseData = Map.of("responses", List.of(Map.of("Response ID", "50", "some_answer", "yes")));
        String encoded = Base64.getEncoder().encodeToString(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsBytes(responseData));
        exportResponse.setResult(encoded);

        when(limeSurveyRcApi.callMethod(any()))
                .thenReturn(sessionResponse) // 1. getSessionKey
                .thenReturn(langResponse)    // 2. getLanguage
                .thenReturn(exportResponse)  // 3. export_responses_by_token
                .thenReturn(new LimeSurveyObjectResponse()); // 4. releaseSessionKey

        Optional<Map<String, Object>> result = limeSurveyRequestService.getAnswer(token, surveyId, savedId);

        assertTrue(result.isPresent());
        assertEquals("50", String.valueOf(result.get().get("Response ID")));
        assertEquals("yes", result.get().get("some_answer"));
    }

    @Test
    void testGetAnswerNotFound() {
        String token = "token123";
        int surveyId = 100;
        int savedId = 50;
        String sessionKey = "session123";

        LimeSurveyObjectResponse sessionResponse = new LimeSurveyObjectResponse();
        sessionResponse.setResult(sessionKey);
        LimeSurveyObjectResponse langResponse = new LimeSurveyObjectResponse();
        langResponse.setResult(Map.of("surveyls_language", "en"));
        LimeSurveyObjectResponse exportResponse = new LimeSurveyObjectResponse();
        exportResponse.setResult(null);

        when(limeSurveyRcApi.callMethod(any()))
                .thenReturn(sessionResponse)
                .thenReturn(langResponse)
                .thenReturn(exportResponse)
                .thenReturn(new LimeSurveyObjectResponse()); // releaseSessionKey

        Optional<Map<String, Object>> result = limeSurveyRequestService.getAnswer(token, surveyId, savedId);

        assertTrue(result.isEmpty());
    }

    @Test
    void testGetAnswerFallsBackToNewestResponseWhenSavedIdIsUnknown() throws Exception {
        // LimeSurvey reuses one token per observation, so a recurring survey accumulates responses.
        // An unknown savedId must not throw the submission away.
        stubExport(List.of(
                Map.of("id", "11", "some_answer", "first"),
                Map.of("id", "12", "some_answer", "second")
        ));

        Optional<Map<String, Object>> result = limeSurveyRequestService.getAnswer("token123", 100, 999);

        assertTrue(result.isPresent());
        assertEquals("second", result.get().get("some_answer"));
    }

    @Test
    void testGetAnswerPrefersTheMatchingSavedId() throws Exception {
        stubExport(List.of(
                Map.of("id", "11", "some_answer", "first"),
                Map.of("id", "12", "some_answer", "second")
        ));

        Optional<Map<String, Object>> result = limeSurveyRequestService.getAnswer("token123", 100, 11);

        assertTrue(result.isPresent());
        assertEquals("first", result.get().get("some_answer"));
    }

    @Test
    void testGetAnswersReturnsEveryResponseForTokenAndSurveyId() throws Exception {
        stubExport(List.of(
                Map.of("id", "11", "token", "token123", "some_answer", "first"),
                Map.of("id", "12", "token", "token123", "some_answer", "second")
        ));

        List<Map<String, Object>> answers = limeSurveyRequestService.getAnswers("token123", 100);

        assertEquals(2, answers.size());
        assertEquals("first", answers.get(0).get("some_answer"));
        assertEquals("second", answers.get(1).get("some_answer"));
        // the survey token must never be stored
        assertFalse(answers.stream().anyMatch(a -> a.containsValue("token123")));
    }

    @Test
    void testGetAnswersIsEmptyForBlankTokenOrSurveyId() {
        assertTrue(limeSurveyRequestService.getAnswers("", 100).isEmpty());
        assertTrue(limeSurveyRequestService.getAnswers("token123", 0).isEmpty());
    }

    @Test
    void testGetAnswerRetriesOnceOnSessionFailure() throws Exception {
        LimeSurveyObjectResponse failedSession = new LimeSurveyObjectResponse();
        failedSession.setError("You have exceeded the number of maximum login attempts");

        LimeSurveyObjectResponse sessionResponse = new LimeSurveyObjectResponse();
        sessionResponse.setResult("session123");
        LimeSurveyObjectResponse langResponse = new LimeSurveyObjectResponse();
        langResponse.setResult(Map.of("surveyls_language", "en"));

        when(limeSurveyRcApi.callMethod(any()))
                .thenReturn(failedSession)   // 1st attempt: get_session_key fails
                .thenReturn(sessionResponse) // retry: get_session_key
                .thenReturn(langResponse)    // retry: getLanguage
                .thenReturn(export(List.of(Map.of("id", "11", "some_answer", "first"))))
                .thenReturn(new LimeSurveyObjectResponse());

        Optional<Map<String, Object>> result = limeSurveyRequestService.getAnswer("token123", 100, 11);

        assertTrue(result.isPresent());
        assertEquals("first", result.get().get("some_answer"));
    }

    private void stubExport(List<Map<String, Object>> responses) throws Exception {
        LimeSurveyObjectResponse sessionResponse = new LimeSurveyObjectResponse();
        sessionResponse.setResult("session123");
        LimeSurveyObjectResponse langResponse = new LimeSurveyObjectResponse();
        langResponse.setResult(Map.of("surveyls_language", "en"));

        when(limeSurveyRcApi.callMethod(any()))
                .thenReturn(sessionResponse)
                .thenReturn(langResponse)
                .thenReturn(export(responses))
                .thenReturn(new LimeSurveyObjectResponse()); // releaseSessionKey
    }

    private static LimeSurveyObjectResponse export(List<Map<String, Object>> responses) throws Exception {
        LimeSurveyObjectResponse exportResponse = new LimeSurveyObjectResponse();
        exportResponse.setResult(Base64.getEncoder().encodeToString(
                new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsBytes(Map.of("responses", responses))));
        return exportResponse;
    }
}
