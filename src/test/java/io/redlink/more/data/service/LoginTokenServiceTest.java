/*
 * Copyright LBI-DHP and/or licensed to LBI-DHP under one or more
 * contributor license agreements (LBI-DHP: Ludwig Boltzmann Institute
 * for Digital Health and Prevention -- A research institute of the
 * Ludwig Boltzmann Gesellschaft, Österreichische Vereinigung zur
 * Förderung der wissenschaftlichen Forschung).
 * Licensed under the Elastic License 2.0.
 */
package io.redlink.more.data.service;

import io.redlink.more.data.configuration.LoginTokenProperties;
import io.redlink.more.data.event.ParticipantUpdateAction;
import io.redlink.more.data.event.ParticipantUpdateEvent;
import io.redlink.more.data.model.LoginToken;
import io.redlink.more.data.model.ParticipantApplication;
import io.redlink.more.data.model.RoutingInfo;
import io.redlink.more.data.repository.LoginTokenRepository;
import io.redlink.more.data.repository.StudyRepository;
import org.apache.commons.codec.binary.Hex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginTokenServiceTest {

    @Mock
    private LoginTokenRepository loginTokenRepository;
    @Mock
    private StudyRepository studyRepository;

    private LoginTokenProperties properties;
    private LoginTokenService loginTokenService;

    private final String hashAlgorithm = "SHA-256";

    @BeforeEach
    void setUp() {
        properties = new LoginTokenProperties();
        properties.setHashAlgorithm(hashAlgorithm);
        loginTokenService = new LoginTokenService(loginTokenRepository, properties, studyRepository);
    }

    @Test
    void testValidateConfigurationSuccess() {
        assertDoesNotThrow(() -> properties.validateConfiguration());
    }

    @Test
    void testValidateConfigurationInvalidHashAlgorithm() {
        properties.setHashAlgorithm("INVALID-ALGORITHM");
        assertThrows(IllegalStateException.class, () -> properties.validateConfiguration());
    }

    @Test
    void testValidateTokenSuccess() throws NoSuchAlgorithmException {
        String code = "test-code";
        Long studyId = 1L;
        Integer participantId = 10;
        String applicationName = "test-app";
        String hashedCode = hash(code);

        ParticipantApplication application = new ParticipantApplication()
                .setStudyId(studyId)
                .setParticipantId(participantId)
                .setApplication(applicationName);

        LoginToken token = new LoginToken()
                .setStudyId(studyId)
                .setParticipantId(participantId)
                .setApplication(applicationName)
                .setCode("encrypted-code")
                .setCodeHash(hashedCode);

        RoutingInfo routingInfo = new RoutingInfo(studyId, participantId, 1, Collections.emptySet(), true, true);

        when(loginTokenRepository.findByCodeHash(hashedCode, application)).thenReturn(Optional.of(token));
        when(studyRepository.getRoutingInfo(studyId, participantId)).thenReturn(Optional.of(routingInfo));

        Optional<RoutingInfo> validatedToken = loginTokenService.validateToken(code, application);

        assertTrue(validatedToken.isPresent());
        assertEquals(studyId, validatedToken.get().studyId());
        assertEquals(participantId, validatedToken.get().participantId());
    }

    @Test
    void testValidateTokenNotFound() throws NoSuchAlgorithmException {
        String code = "test-code";
        Long studyId = 1L;
        Integer participantId = 10;
        String applicationName = "test-app";
        String hashedCode = hash(code);

        ParticipantApplication application = new ParticipantApplication()
                .setStudyId(studyId)
                .setParticipantId(participantId)
                .setApplication(applicationName);

        when(loginTokenRepository.findByCodeHash(hashedCode, application)).thenReturn(Optional.empty());

        Optional<RoutingInfo> validatedToken = loginTokenService.validateToken(code, application);

        assertFalse(validatedToken.isPresent());
    }

    @Test
    void testValidateTokenNullCode() {
        Long studyId = 1L;
        Integer participantId = 10;
        String applicationName = "test-app";

        ParticipantApplication application = new ParticipantApplication()
                .setStudyId(studyId)
                .setParticipantId(participantId)
                .setApplication(applicationName);

        when(loginTokenRepository.findByCodeHash(null, application)).thenReturn(Optional.empty());

        Optional<RoutingInfo> validatedToken = loginTokenService.validateToken(null, application);

        assertFalse(validatedToken.isPresent());
    }

    @Test
    void testValidateTokenTokenFoundButNoRoutingInfo() throws NoSuchAlgorithmException {
        String code = "test-code";
        Long studyId = 1L;
        Integer participantId = 10;
        String applicationName = "test-app";
        String hashedCode = hash(code);

        ParticipantApplication application = new ParticipantApplication()
                .setStudyId(studyId)
                .setParticipantId(participantId)
                .setApplication(applicationName);

        LoginToken token = new LoginToken()
                .setStudyId(studyId)
                .setParticipantId(participantId)
                .setApplication(applicationName);

        when(loginTokenRepository.findByCodeHash(hashedCode, application)).thenReturn(Optional.of(token));
        when(studyRepository.getRoutingInfo(studyId, participantId)).thenReturn(Optional.empty());

        Optional<RoutingInfo> validatedToken = loginTokenService.validateToken(code, application);

        assertFalse(validatedToken.isPresent());
    }

    @Test
    void testOnApplicationEventDelete() {
        Long studyId = 1L;
        Integer participantId = 10;
        ParticipantUpdateEvent event = new ParticipantUpdateEvent(this, studyId, participantId, ParticipantUpdateAction.DELETE);

        loginTokenService.onApplicationEvent(event);

        verify(loginTokenRepository).deleteLoginTokens(studyId, participantId);
    }

    private String hash(String input) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance(hashAlgorithm);
        byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
        return Hex.encodeHexString(hash);
    }
}
