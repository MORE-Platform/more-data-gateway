/*
 * Copyright LBI-DHP and/or licensed to LBI-DHP under one or more
 * contributor license agreements (LBI-DHP: Ludwig Boltzmann Institute
 * for Digital Health and Prevention -- A research institute of the
 * Ludwig Boltzmann Gesellschaft, Österreichische Vereinigung zur
 * Förderung der wissenschaftlichen Forschung).
 * Licensed under the Elastic License 2.0.
 */
package io.redlink.more.data.service;

import io.redlink.more.data.event.ParticipantUpdateAction;
import io.redlink.more.data.event.ParticipantUpdateEvent;
import io.redlink.more.data.model.ParticipantApplication;
import io.redlink.more.data.model.ParticipantConsent;
import io.redlink.more.data.model.RoutingInfo;
import io.redlink.more.data.repository.ParticipantApplicationRepository;
import io.redlink.more.data.repository.StudyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApplicationAccessServiceTest {

    @Mock
    private LoginTokenService loginTokenService;

    @Mock
    private ParticipantApplicationRepository participantApplicationRepository;

    @Mock
    private StudyRepository studyRepository;

    private ApplicationAccessService applicationAccessService;

    @BeforeEach
    void setUp() {
        applicationAccessService = new ApplicationAccessService(loginTokenService, participantApplicationRepository, studyRepository);
    }


    @Test
    void testValidateLoginSuccess() {
        Long studyId = 1L;
        String userDataRef = "user-ref";
        String loginToken = "token";
        ParticipantApplication application = new ParticipantApplication();
        RoutingInfo routingInfo = new RoutingInfo(studyId, 1, OptionalInt.empty(), Set.of(), true, true);

        when(participantApplicationRepository.findByUserDataReference(studyId, userDataRef)).thenReturn(Optional.of(application));
        when(loginTokenService.validateToken(loginToken, application)).thenReturn(Optional.of(routingInfo));

        Optional<RoutingInfo> result = applicationAccessService.validateLogin(studyId, userDataRef, loginToken);

        assertTrue(result.isPresent());
        assertEquals(routingInfo, result.get());
    }

    @Test
    void testValidateLoginApplicationNotFound() {
        Long studyId = 1L;
        String userDataRef = "user-ref";
        String loginToken = "token";

        when(participantApplicationRepository.findByUserDataReference(studyId, userDataRef)).thenReturn(Optional.empty());

        Optional<RoutingInfo> result = applicationAccessService.validateLogin(studyId, userDataRef, loginToken);

        assertFalse(result.isPresent());
        verifyNoInteractions(loginTokenService);
    }

    @Test
    void testHasConsentAccepted() {
        RoutingInfo routingInfo = new RoutingInfo(1L, 1, OptionalInt.empty(), Set.of(), true, true);
        ParticipantConsent consent = new ParticipantConsent(true, "device", "md5", null, null);

        when(studyRepository.getConsent(1L, 1)).thenReturn(Optional.of(consent));

        assertTrue(applicationAccessService.hasConsent(routingInfo));
    }

    @Test
    void testHasConsentNotAccepted() {
        RoutingInfo routingInfo = new RoutingInfo(1L, 1, OptionalInt.empty(), Set.of(), true, true);
        ParticipantConsent consent = new ParticipantConsent(false, "device", "md5", null, null);

        when(studyRepository.getConsent(1L, 1)).thenReturn(Optional.of(consent));

        assertFalse(applicationAccessService.hasConsent(routingInfo));
    }

    @Test
    void testHasConsentNotFound() {
        RoutingInfo routingInfo = new RoutingInfo(1L, 1, OptionalInt.empty(), Set.of(), true, true);

        when(studyRepository.getConsent(1L, 1)).thenReturn(Optional.empty());

        assertFalse(applicationAccessService.hasConsent(routingInfo));
    }

    @Test
    void testValidateAndStoreConsentSuccess() {
        RoutingInfo routingInfo = new RoutingInfo(1L, 1, OptionalInt.empty(), Set.of(), true, true);
        ParticipantConsent consent = new ParticipantConsent(true, "device", "md5", null, null);

        applicationAccessService.validateAndStoreConsent(routingInfo, consent);

        verify(studyRepository).storeConsent(1L, 1, consent);
    }

    @Test
    void testValidateAndStoreConsentThrowsException() {
        RoutingInfo routingInfo = new RoutingInfo(1L, 1, OptionalInt.empty(), Set.of(), true, true);
        ParticipantConsent consent = new ParticipantConsent(false, "device", "md5", null, null);

        assertThrows(IllegalStateException.class, () -> applicationAccessService.validateAndStoreConsent(routingInfo, consent));
        verifyNoInteractions(studyRepository);
    }

    @Test
    void testOnApplicationEventDelete() {
        Long studyId = 1L;
        Integer participantId = 1;
        ParticipantUpdateEvent event = new ParticipantUpdateEvent(this, studyId, participantId, ParticipantUpdateAction.DELETE);

        applicationAccessService.onApplicationEvent(event);

        verify(participantApplicationRepository).deleteAllByParticipant(studyId, participantId);
    }
}
