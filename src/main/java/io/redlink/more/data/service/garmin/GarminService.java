package io.redlink.more.data.service.garmin;

import io.redlink.more.data.custom.model.GarminDataPoint;
import io.redlink.more.data.event.ParticipantUpdateEvent;
import io.redlink.more.data.garmin.wellness.ApiClient;
import io.redlink.more.data.garmin.wellness.client.UserApiApi;
import io.redlink.more.data.garmin.wellness.client.UserControllerApi;
import io.redlink.more.data.model.DataPoint;
import io.redlink.more.data.model.Observation;
import io.redlink.more.data.model.ParticipantKeyValue;
import io.redlink.more.data.model.ParticipantWithObservationProperties;
import io.redlink.more.data.model.RoutingInfo;
import io.redlink.more.data.model.garmin.GarminAuthenticationValues;
import io.redlink.more.data.model.garmin.GarminSummaryType;
import io.redlink.more.data.model.garmin.GarminUserAccessToken;
import io.redlink.more.data.model.garmin.ParticipantGarminDataPoint;
import io.redlink.more.data.model.garmin.UserAccessTokenWithData;
import io.redlink.more.data.properties.GarminProperties;
import io.redlink.more.data.repository.ParticipantKeyValueRepository;
import io.redlink.more.data.repository.StudyRepository;
import io.redlink.more.data.service.ElasticService;
import io.redlink.more.data.util.DataUtils;
import io.redlink.more.data.util.DateTimeUtils;
import io.redlink.more.data.util.GarminUtils;
import org.apache.commons.lang3.Range;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class GarminService implements ApplicationListener<ParticipantUpdateEvent> {
    private final Logger LOG = LoggerFactory.getLogger(GarminService.class);
    public final static String AUTH_VALUES_KEY = "authenticationValues";
    public final static String USER_ACCESS_TOKEN_KEY = "userAccessToken";
    private final static String USER_ID_TYPE_KEY = "keyType";
    private final static String USER_PERMISSIONS_KEY = "permissions";
    public final static String GARMIN_KEY_TYPE = "garmin";
    private final GarminProperties garminProperties;
    private final RestTemplate restTemplate;
    private final StudyRepository studyRepository;
    private final ParticipantKeyValueRepository participantKeyValueRepository;
    private final ElasticService elasticService;

    private final UserApiApi userApi;
    private final UserControllerApi userControllerApi;

    private final GarminDataTransformationService transformationService;
    private final Executor asyncExecutor;

    public GarminService(GarminProperties properties, StudyRepository studyRepository, ParticipantKeyValueRepository participantKeyValueRepository, ElasticService elasticService, UserApiApi userApi, UserControllerApi userControllerApi, GarminDataTransformationService transformationService) {
        this.garminProperties = properties;
        this.studyRepository = studyRepository;
        this.participantKeyValueRepository = participantKeyValueRepository;
        this.elasticService = elasticService;
        this.userControllerApi = userControllerApi;
        this.transformationService = transformationService;
        this.restTemplate = new RestTemplate();
        this.userApi = userApi;
        this.asyncExecutor = Executors.newFixedThreadPool(5);
    }

    public String getSsoUrl(RoutingInfo routingInfo, String requestUrl) {
        LOG.info("Requesting Garmin SSO URL for studyId={}, participantId={}", routingInfo.studyId(), routingInfo.participantId());
        List<Observation> observations = studyRepository.filterObservations(
                routingInfo,
                true,
                observation -> observation.type().toLowerCase().contains(GARMIN_KEY_TYPE),
                routingInfo.observationGroupIds());
        if (observations.isEmpty()) {
            throw new IllegalStateException("No Garmin Observations found for " + routingInfo.participantId());
        }

        if (hasValidUserAccessToken(routingInfo)) {
            LOG.info("Existing valid Garmin access token found for studyId={}, participantId={}", routingInfo.studyId(), routingInfo.participantId());
            return garminProperties.getRedirectUri();
        }

        URI baseUri = garminProperties.basicOAuthUri(requestUrl);

        String codeVerifier = GarminUtils.createCodeVerifier();
        String state = GarminUtils.garminOAuthState();

        GarminAuthenticationValues authenticationValues = new GarminAuthenticationValues(state, codeVerifier, requestUrl);
        Map<String, Object> properties = Map.of(AUTH_VALUES_KEY, authenticationValues.toMap());
        LOG.debug("Storing Garmin OAuth state for {} observations: studyId={}, participantId={}, state={}", observations.size(), routingInfo.studyId(), routingInfo.participantId(), state);
        observations.forEach(observation ->
                studyRepository.mergeParticipantProperties(routingInfo.studyId(), routingInfo.participantId(), observation.observationId(), properties)
        );

        String codeChallenge = GarminUtils.createCodeChallenge(codeVerifier);
        String oauthUrl = baseUri + "&code_challenge=" + codeChallenge + "&code_challenge_method=S256" + "&state=" + state;

        try {
            HttpHeaders headers = new HttpHeaders();
            HttpEntity<?> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    oauthUrl,
                    HttpMethod.GET,
                    entity,
                    String.class
            );

            String resolvedUrl = response.getHeaders().getLocation() != null
                    ? response.getHeaders().getLocation().toString()
                    : oauthUrl;
            LOG.debug("Resolved Garmin OAuth redirect URL: {}", resolvedUrl);
            return resolvedUrl;
        } catch (RuntimeException e) {
            LOG.error("Error while requesting OAuth URL", e);
            return oauthUrl;
        }
    }

    public void ssoCallback(String state, String code) {
        LOG.info("Processing Garmin SSO callback: statePresent={}, codePresent={}", state != null, code != null);
        var participantWithObservationPropertiesList = studyRepository.getParticipantByGarminStatus(state);
        if (participantWithObservationPropertiesList.isEmpty()) {
            LOG.warn("No participant found for Garmin state={}", state);
            throw new IllegalStateException("No Garmin Status found for state " + state);
        }

        var participantWithObservationProperties = participantWithObservationPropertiesList.stream().findFirst().orElseThrow();
        LOG.debug("Found participant for state: studyId={}, participantId={}", participantWithObservationProperties.studyId(), participantWithObservationProperties.participantId());
        var garminAccessToken = queryUserAccessToken(participantWithObservationProperties, code);
        if (garminAccessToken.isEmpty()) {
            LOG.error("Failed to obtain Garmin access token for participantId={} in studyId={}", participantWithObservationProperties.participantId(), participantWithObservationProperties.studyId());
            throw new IllegalStateException("No Garmin Access Token found for " + participantWithObservationPropertiesList.get(0).participantId());
        }
        LOG.info("Successfully obtained Garmin access token (metadata only) for participantId={} in studyId={}", participantWithObservationProperties.participantId(), participantWithObservationProperties.studyId());
        var userAccesstokenWithData = UserAccessTokenWithData.createNewFrom(garminAccessToken.get());
        storeUserAccessToken(userAccesstokenWithData, participantWithObservationPropertiesList, participantWithObservationProperties);
        addAuthorizationHeader(userApi.getApiClient(), userAccesstokenWithData);
        addAuthorizationHeader(userControllerApi.getApiClient(), userAccesstokenWithData);
        storeGarminUserId(participantWithObservationProperties.studyId(), participantWithObservationProperties.participantId());
        LOG.info("Garmin SSO callback processing completed for studyId={}, participantId={}", participantWithObservationProperties.studyId(), participantWithObservationProperties.participantId());
    }


    public boolean deleteUserIdAndToken(String userId) {
        LOG.info("Deleting Garmin userId and token for userId={}", userId);
        var participants = participantForGarminUserId(userId);
        if (participants.isEmpty()) {
            LOG.info("No participants associated with Garmin userId={}, nothing to delete", userId);
            return true;
        }

        boolean result = participants.stream().allMatch(participant ->
                deleteGarminUserId(participant.studyId(), participant.participantId(), userId)
                        && deleteUserAccessToken(participant.studyId(), participant.participantId())
        );
        LOG.info("Deletion of Garmin userId={} across {} participant(s) result={}", userId, participants.size(), result);
        return result;
    }

    public boolean updateUserPermissions(String userId, List<String> permissions) {
        LOG.info("Updating Garmin permissions for userId={} with {} permission(s)", userId, permissions != null ? permissions.size() : 0);
        var participants = participantForGarminUserId(userId);
        if (participants.isEmpty()) {
            LOG.warn("No participants found for Garmin userId={}, cannot update permissions", userId);
            return false;
        }
        participants.forEach(participant -> {
                    LOG.debug("Upserting permissions for studyId={}, participantId={}", participant.studyId(), participant.participantId());
                    participantKeyValueRepository.upsert(
                            participant.studyId(),
                            participant.participantId(),
                            userId,
                            Map.of(USER_ID_TYPE_KEY, GARMIN_KEY_TYPE, USER_PERMISSIONS_KEY, permissions)
                    );
                }
        );
        LOG.info("Permissions update completed for userId={} across {} participant(s)", userId, participants.size());
        return true;
    }

    public void deregisterParticipant(Long studyId, int participantId) {
        LOG.info("Deregistering Garmin for studyId={}, participantId={}", studyId, participantId);
        try {
            var token = refreshTokenIfNeeded(studyId, participantId);
            if (token != null) {
                addAuthorizationHeader(userApi.getApiClient(), token);
                addAuthorizationHeader(userControllerApi.getApiClient(), token);
                LOG.debug("Sending deregistration to Garmin for studyId={}, participantId={}", studyId, participantId);
                try {
                    sendDeregistration();
                    LOG.info("Successfully sent deregistration to Garmin for studyId={}, participantId={}", studyId, participantId);
                } catch (RuntimeException e) {
                    LOG.error("Failed to send deregistration to Garmin for studyId={}, participantId={}, but continuing with local cleanup", studyId, participantId, e);
                }
            } else {
                LOG.warn("No valid token available for Garmin deregistration, skipping API call but proceeding with local cleanup for studyId={}, participantId={}", studyId, participantId);
            }

            boolean tokenDeleted = deleteUserAccessToken(studyId, participantId);
            LOG.debug("Deleted user access token for studyId={}, participantId={}, result={}", studyId, participantId, tokenDeleted);
            var participantWithKeyValue = getKeyValuesForParticipant(studyId, participantId);
            participantWithKeyValue.forEach(participantKeyValue -> {
                        boolean kvDeleted = deleteGarminUserId(studyId, participantId, participantKeyValue.key());
                        LOG.debug("Deleted Garmin userId mapping key={} for studyId={}, participantId={}, result={}", participantKeyValue.key(), studyId, participantId, kvDeleted);
                    }
            );
            LOG.info("Completed local cleanup for Garmin deregistration: studyId={}, participantId={}", studyId, participantId);
        } catch (EmptyResultDataAccessException e) {
            LOG.error("Could not deregister Garmin User for participant {} of study {}:", participantId, studyId, e);
        }
    }

    public boolean garminRequestIsValid(String userAgent, String cliendId) {
        return userAgent != null && userAgent.equalsIgnoreCase("Garmin Health API") && cliendId != null && garminProperties.clientIdsMatch(cliendId);
    }

    // This method takes all provided data from garmin, transforms it using the GarminTransformationService flattens
    // the returned list of maps into a single map which it stores into the elastic search
    public void storeData(Map<GarminSummaryType, List<GarminDataPoint>> data) throws IOException {
        if (data == null || data.isEmpty()) {
            return;
        }

        var entries = data.entrySet()
                .stream()
                .map(entry -> {
                    var participants = participantsForUserIds(entry.getValue());
                    return transformationService.transformData(entry.getKey(), participants);
                })
                .flatMap(map -> map.entrySet().stream())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> new ArrayList<>(e.getValue()),
                        (left, right) -> {
                            left.addAll(right); // This flattens the list of maps into a map with combined list of datapoints
                            return left;
                        }
                )).entrySet();

        if (entries.isEmpty()) {
            LOG.info("No data to store from Garmin...");
            return;
        }

        for (Map.Entry<RoutingInfo, ArrayList<DataPoint>> mapEntry : entries) {
            RoutingInfo key = mapEntry.getKey();
            ArrayList<DataPoint> value = mapEntry.getValue();
            deduplicateDataPoints(value, key);
            elasticService.storeDataPoints(value, key);
            LOG.debug("Stored {} datapoints for user {}", value.size(), key);
        }
    }

    public void refreshAllTokens() {
        LOG.info("Refreshing Garmin token refresh task");
        getAllGarminParticipants()
                .forEach((key, value) ->
                        refreshTokenIfNeeded(value, key));
    }

    private void storeGarminUserId(Long studyId, int participantId) {
        var userId = getUserId();
        if (userId.isEmpty()) {
            LOG.error("No Garmin User ID returned from API for studyId={}, participantId={}", studyId, participantId);
            throw new IllegalStateException("No Garmin User ID found for " + participantId);
        }
        var permissions = getPermissions();
        Map<String, Object> data = Map.of(USER_ID_TYPE_KEY, "garmin", USER_PERMISSIONS_KEY, permissions);
        participantKeyValueRepository.upsert(studyId, participantId, userId.get(), data);
        LOG.info("Stored/updated Garmin userId mapping for studyId={}, participantId={} with {} permission(s)", studyId, participantId, permissions != null ? permissions.size() : 0);
    }

    private void deduplicateDataPoints(List<DataPoint> dataBulk, RoutingInfo routingInfo) throws IOException {
        Map<String, Set<Range<Instant>>> timeRangesByDataType =
                dataBulk.stream()
                        .collect(Collectors.groupingBy(
                                DataPoint::dataType,
                                Collectors.mapping(DataUtils::buildRangeFromDataPoint, Collectors.toSet())
                        ));
        for (Map.Entry<String, Set<Range<Instant>>> entry : timeRangesByDataType.entrySet()) {
            String dataType = entry.getKey();
            Set<Range<Instant>> ranges = DateTimeUtils.mergeRanges(entry.getValue());
            long deleted = elasticService.deleteDataPointsInTimeRanges(routingInfo, dataType, ranges);
            LOG.info("Cleared {} datapoints for studyId={}, participantId={} for Garmin datatype={}: rangesCount={}", deleted, routingInfo.studyId(), routingInfo.participantId(), dataType, ranges.size());
        }
    }

    private Optional<String> getUserId() {
        LOG.debug("Requesting Garmin userId via UserApi");
        var response = userApi.uSERID();
        String userId = response.getUserId();
        LOG.debug("Received Garmin userId present={} ", userId != null);
        return Optional.ofNullable(userId);
    }

    private List<String> getPermissions() {
        try {
            LOG.debug("Requesting Garmin user permissions via UserControllerApi");
            var response = userControllerApi.gETUSERPERMISSIONS();
            List<String> permissions = response.getPermissions();
            LOG.debug("Received Garmin permissions count={}", permissions != null ? permissions.size() : 0);
            return permissions;
        } catch (RuntimeException e) {
            LOG.error("Could not get Garmin user permissions via UserControllerApi", e);
            return Collections.emptyList();
        }
    }

    private List<ParticipantKeyValue> participantForGarminUserId(String garminUserId) {
        LOG.debug("Looking up participants for Garmin userId={}", garminUserId);
        var result = participantKeyValueRepository.getByKey(garminUserId);
        List<ParticipantKeyValue> filtered = result.stream().filter(participantKeyValue ->
                participantKeyValue.value().containsKey(USER_ID_TYPE_KEY)
                        && participantKeyValue.value().get(USER_ID_TYPE_KEY).equals(GARMIN_KEY_TYPE)
        ).toList();
        List<ParticipantKeyValue> active = filtered
                .stream()
                .filter(participantKeyValue -> {
                    String studyStatus = studyRepository
                            .getStudyState(participantKeyValue.studyId()).orElse(null);
                    if (studyStatus == null || (!studyStatus.equals("active") && !studyStatus.equals("preview"))) {
                        return false;
                    }
                    String participantStatus = studyRepository
                            .getParticipantState(participantKeyValue.studyId(), participantKeyValue.participantId()).orElse(null);
                    return participantStatus != null && participantStatus.equals("active");
                }).toList();
        List<ParticipantKeyValue> nonActive = filtered
                .stream()
                .filter(participantKeyValue ->
                        !active.contains(participantKeyValue)
                ).toList();

        if (!nonActive.isEmpty()) {
            LOG.info("Found {} non-active participant(s) for Garmin userId={}, triggering async deregistration", nonActive.size(), garminUserId);
            deregisterNonActiveParticipantsAsync(nonActive);
        }

        LOG.debug("Found {} participant(s) for Garmin userId={}", active.size(), garminUserId);
        return active;
    }

    private void deregisterNonActiveParticipantsAsync(List<ParticipantKeyValue> nonActiveParticipants) {
        CompletableFuture.runAsync(() -> {
            LOG.info("Starting async deregistration for {} non-active participant(s)", nonActiveParticipants.size());
            nonActiveParticipants.forEach(participantKeyValue -> {
                try {
                    LOG.debug("Deregistering non-active participant: studyId={}, participantId={}", participantKeyValue.studyId(), participantKeyValue.participantId());
                    deregisterParticipant(participantKeyValue.studyId(), participantKeyValue.participantId());
                    LOG.info("Successfully deregistered non-active participant: studyId={}, participantId={}", participantKeyValue.studyId(), participantKeyValue.participantId());
                } catch (RuntimeException e) {
                    LOG.error("Failed to deregister non-active participant: studyId={}, participantId={}: {}", participantKeyValue.studyId(), participantKeyValue.participantId(), e.toString());
                }
            });
            LOG.info("Completed async deregistration for {} non-active participant(s)", nonActiveParticipants.size());
        }, asyncExecutor);
    }

    private List<ParticipantKeyValue> getKeyValuesForParticipant(Long studyId, Integer participantId) {
        LOG.debug("Fetching key-values for participant: studyId={}, participantId={}", studyId, participantId);
        var result = participantKeyValueRepository.getKeysWithValue(studyId, participantId);
        List<ParticipantKeyValue> filtered = result.stream().filter(participantKeyValue ->
                participantKeyValue.value().containsKey(USER_ID_TYPE_KEY)
                        && participantKeyValue.value().get(USER_ID_TYPE_KEY).equals(GARMIN_KEY_TYPE)
        ).toList();
        LOG.debug("Found {} Garmin-related key-values for studyId={}, participantId={}", filtered.size(), studyId, participantId);
        return filtered;
    }

    private boolean deleteGarminUserId(Long studyId, int participantId, String garminUserId) {
        boolean result = participantKeyValueRepository.delete(studyId, participantId, garminUserId, Map.of(USER_ID_TYPE_KEY, GARMIN_KEY_TYPE));
        LOG.debug("Deleted Garmin userId key={} for studyId={}, participantId={}, result={}", garminUserId, studyId, participantId, result);
        return result;
    }

    private void sendDeregistration() {
        LOG.info("Calling Garmin deregistration endpoint");
        userApi.dEREG();
        LOG.debug("Garmin deregistration call completed");
    }

    private Optional<GarminUserAccessToken> queryUserAccessToken(ParticipantWithObservationProperties participantWithObservationProperties, String code) {
        if (!participantWithObservationProperties.properties().containsKey(AUTH_VALUES_KEY) || !(participantWithObservationProperties.properties().get(AUTH_VALUES_KEY) instanceof Map)) {
            throw new IllegalStateException("No Garmin Access Token found for " + participantWithObservationProperties.participantId());
        }
        LOG.info("Exchanging Garmin OAuth code for access token: studyId={}, participantId={}", participantWithObservationProperties.studyId(), participantWithObservationProperties.participantId());
        var garminAuthValues = GarminAuthenticationValues.fromMap((Map<String, String>) participantWithObservationProperties.properties().get(AUTH_VALUES_KEY));

        HttpEntity<?> entity = getAuthorizationCodeHttpEntity(code, garminAuthValues);

        return requestAccessToken(entity);
    }

    // The User access tokens are being accessed and the participants are being grouped by the accessToken, as one access token can be applied to multiple participants
    private Map<UserAccessTokenWithData, List<ParticipantWithObservationProperties>> getAllGarminParticipants() {
        var participantWithObservationProperties = studyRepository.getParticipantObservationPropertiesByKeyExists(USER_ACCESS_TOKEN_KEY);
        return participantWithObservationProperties.stream()
                .map(pop -> Map.entry(pop, getUserAccessData(pop)))
                .filter(entry -> entry.getValue().isPresent())
                .map(entry -> Map.entry(entry.getValue().get(), entry.getKey()))
                .collect(Collectors.groupingBy(
                        Map.Entry::getKey,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toList())
                ));
    }

    private UserAccessTokenWithData refreshUserAccessToken(UserAccessTokenWithData userAccessTokenWithData) {
        if (userAccessTokenWithData.isAccessTokenExpired()) {
            if (!userAccessTokenWithData.isRefreshAccessTokenExpired()) {
                HttpEntity<?> entity = getRefreshTokenHttpEntity(userAccessTokenWithData.accessToken().refreshToken());

                return requestAccessToken(entity)
                        .map(UserAccessTokenWithData::createNewFrom)
                        .orElseThrow(() -> new IllegalStateException("Failed to refresh access token"));
            }
            throw new IllegalStateException("Access token is not valid and refreshable!");
        }
        return userAccessTokenWithData;
    }

    private Optional<GarminUserAccessToken> requestAccessToken(HttpEntity<?> entity) {
        ResponseEntity<GarminUserAccessToken> response = restTemplate.exchange(
                garminProperties.garminTokenUri(),
                HttpMethod.POST,
                entity,
                GarminUserAccessToken.class
        );
        LOG.debug("Token exchange HTTP status: {}", response.getStatusCode());

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            return Optional.ofNullable(response.getBody());
        }
        throw new IllegalStateException("Issue requesting access token with status code " + response.getStatusCode());
    }

    private HttpEntity<?> getAuthorizationCodeHttpEntity(String code, GarminAuthenticationValues garminAuthValues) {
        String body = "grant_type=authorization_code" +
                "&redirect_uri=" + garminProperties.getRedirectUri() +
                "&code=" + code +
                "&code_verifier=" + garminAuthValues.challengeCode();

        return createTokenRequestEntity(body);
    }

    private HttpEntity<?> getRefreshTokenHttpEntity(String refreshToken) {
        String body = "grant_type=refresh_token" +
                "&refresh_token=" + refreshToken;

        return createTokenRequestEntity(body);
    }

    private HttpEntity<?> createTokenRequestEntity(String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_TYPE, "application/x-www-form-urlencoded");
        headers.add(HttpHeaders.AUTHORIZATION, garminProperties.authorizationHeader());
        LOG.debug("Prepared token exchange request with redirectUri={} and code_verifier_present=true", garminProperties.getRedirectUri());
        return new HttpEntity<>(body, headers);
    }

    private Boolean hasValidUserAccessToken(RoutingInfo routingInfo) {
        return hasValidUserAccessToken(routingInfo.studyId(), routingInfo.participantId());
    }


    private Boolean hasValidUserAccessToken(Long studyId, int participantId) {
        var userAccessData = getUserAccessData(studyId, participantId);
        return userAccessData.isPresent() && userAccessData.get().isAccessTokenValidOrRefreshable();
    }

    private Optional<UserAccessTokenWithData> getUserAccessData(Long studyId, int participantId) {
        var properties = studyRepository.getParticipantObservationPropertiesByKeyExists(studyId, participantId, USER_ACCESS_TOKEN_KEY);
        if (properties.isEmpty()) {
            return Optional.empty();
        }
        return getUserAccessData(properties.get(0));
    }


    private Optional<UserAccessTokenWithData> getUserAccessData(ParticipantWithObservationProperties participantWithObservationProperties) {
        if (!participantWithObservationProperties.properties().containsKey(USER_ACCESS_TOKEN_KEY) || !(participantWithObservationProperties.properties().get(USER_ACCESS_TOKEN_KEY) instanceof Map)) {
            return Optional.empty();
        }
        return Optional.of(UserAccessTokenWithData.fromMap((Map<String, Object>) participantWithObservationProperties.properties().get(USER_ACCESS_TOKEN_KEY)));
    }

    private void storeUserAccessToken(UserAccessTokenWithData userAccesstokenWithData, List<ParticipantWithObservationProperties> participantWithObservationPropertiesList, ParticipantWithObservationProperties participantWithObservationProperties) {
        Map<String, Object> map = Map.of(USER_ACCESS_TOKEN_KEY, userAccesstokenWithData.toMap());
        participantWithObservationPropertiesList
                .stream()
                .filter(properties -> properties.participantId().equals(participantWithObservationProperties.participantId()))
                .map(ParticipantWithObservationProperties::observationId)
                .forEach(observationId -> studyRepository
                        .mergeParticipantProperties(
                                participantWithObservationProperties.studyId(),
                                participantWithObservationProperties.participantId(),
                                observationId,
                                map
                        )
                );

    }

    private UserAccessTokenWithData refreshTokenIfNeeded(Long studyId, int participantId) {
        var token = getUserAccessData(studyId, participantId);
        return token.map(this::refreshTokenIfNeeded).orElse(null);
    }

    private UserAccessTokenWithData refreshTokenIfNeeded(UserAccessTokenWithData token) {
        var participantWithObservationPropertiesList = studyRepository.getParticipantByGarminAccessToken(token.accessToken().accessToken());
        return refreshTokenIfNeeded(participantWithObservationPropertiesList, token);
    }

    private UserAccessTokenWithData refreshTokenIfNeeded(List<ParticipantWithObservationProperties> participantWithObservationPropertiesList, UserAccessTokenWithData token) {
        if (!participantWithObservationPropertiesList.isEmpty() && token.isAccessTokenExpired() && !token.isRefreshAccessTokenExpired()) {
            LOG.info("Access token is expired and refreshable, refreshing token");
            var newToken = refreshUserAccessToken(token);
            if (!newToken.getAccessToken().equals(token.accessToken().accessToken())) {
                LOG.info("Refreshed token...");
                var participantWithObservationProperties = participantWithObservationPropertiesList.stream().findFirst().orElseThrow();
                storeUserAccessToken(newToken, participantWithObservationPropertiesList, participantWithObservationProperties);
                LOG.info("Refreshed token for participantId={} in studyId={}", participantWithObservationProperties.participantId(), participantWithObservationProperties.studyId());
            }
            return newToken;
        } else if (token.isRefreshAccessTokenExpired()) {
            participantWithObservationPropertiesList.forEach(participantWithObservationProperties ->
                    deleteUserAccessToken(participantWithObservationProperties.studyId(), participantWithObservationProperties.participantId()));
            return null;
        }
        return token;
    }

    private boolean deleteUserAccessToken(Long studyId, int participantId) {
        List<Observation> observations = studyRepository.filterObservations(
                studyId,
                participantId,
                observation -> observation.type().toLowerCase().contains(GARMIN_KEY_TYPE)
        );
        if (observations.isEmpty()) {
            return true;
        }
        try {
            observations.forEach(observation -> studyRepository.removeParticipantPropertyKey(studyId, participantId, observation.observationId(), USER_ACCESS_TOKEN_KEY));
            return true;
        } catch (EmptyResultDataAccessException e) {
            LOG.error("Error while deleting User Access Token", e);
            return false;
        }
    }

    private void addAuthorizationHeader(ApiClient apiClient, UserAccessTokenWithData userAccessTokenWithData) {
        apiClient.addDefaultHeader(
                HttpHeaders.AUTHORIZATION,
                StringUtils.capitalize(userAccessTokenWithData.accessToken().tokenType()) + " " + userAccessTokenWithData.accessToken().accessToken()
        );
    }


    private List<ParticipantGarminDataPoint> participantsForUserIds(List<GarminDataPoint> dataPoints) {
        return dataPoints.stream()
                .map(GarminDataPoint::getUserId)
                .distinct()
                .flatMap(userId -> {
                    try {
                        return participantForGarminUserId(userId).stream();
                    } catch (EmptyResultDataAccessException e) {
                        LOG.warn("No participant found for Garmin userId={}", userId);
                        return Stream.empty();
                    }
                })
                .map(participantKeyValue ->
                        new ParticipantGarminDataPoint(
                                participantKeyValue,
                                dataPoints.stream()
                                        .filter(dp -> dp.getUserId().equals(participantKeyValue.key()))
                                        .toList()))
                .toList();
    }


    @Override
    public void onApplicationEvent(ParticipantUpdateEvent event) {
        switch (event.getAction()) {
            case DELETE -> deregisterParticipant(event.getStudyId(), event.getParticipantId());
        }
    }
}
