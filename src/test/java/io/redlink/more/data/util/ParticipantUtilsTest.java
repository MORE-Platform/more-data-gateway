package io.redlink.more.data.util;

import io.redlink.more.data.model.ParticipantConsent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParticipantUtilsTest {

    @Test
    void testObfuscate() {
        assertEquals("unknown", ParticipantUtils.obfuscate(null));
        assertEquals("unknown", ParticipantUtils.obfuscate(""));

        // With delimiter
        assertEquals("Model#123456789012", ParticipantUtils.obfuscate("Model#123456789012")); // Exactly 12, not obfuscated
        assertEquals("Model#123456...890123", ParticipantUtils.obfuscate("Model#1234567890123"));
        assertEquals("Model#123", ParticipantUtils.obfuscate("Model#123"));
        assertEquals("unknown#123456...890123", ParticipantUtils.obfuscate("#1234567890123"));

        // Without delimiter
        assertEquals("123456", ParticipantUtils.obfuscate("123456"));
        assertEquals("123456...890123", ParticipantUtils.obfuscate("1234567890123"));
    }

    @Test
    void testConvertAppDTO() {
        io.redlink.more.data.api.app.v1.model.StudyConsentDTO dto = new io.redlink.more.data.api.app.v1.model.StudyConsentDTO();
        dto.setConsent(true);
        dto.setDeviceId("Device#1234567890123");
        dto.setConsentInfoMD5("md5hash");

        io.redlink.more.data.api.app.v1.model.ObservationConsentDTO obs = new io.redlink.more.data.api.app.v1.model.ObservationConsentDTO();
        obs.setObservationId("1");
        dto.setObservations(List.of(obs));

        ParticipantConsent result = ParticipantUtils.convert(dto);
        assertTrue(result.accepted());
        assertEquals("Device#123456...890123", result.deviceId());
        assertEquals("md5hash", result.consentMd5());
        assertEquals(1, result.observationConsents().size());
        assertEquals(1, result.observationConsents().get(0).observationId());
    }

    @Test
    void testConvertParticipantDTO() {
        io.redlink.more.data.api.participant.v1.model.StudyConsentDTO dto = new io.redlink.more.data.api.participant.v1.model.StudyConsentDTO();
        dto.setConsent(true);
        dto.setDeviceId("Device#1234567890123");
        dto.setConsentInfoMD5("md5hash");

        io.redlink.more.data.api.participant.v1.model.ObservationConsentDTO obs = new io.redlink.more.data.api.participant.v1.model.ObservationConsentDTO();
        obs.setObservationId("2");
        dto.setObservations(List.of(obs));

        ParticipantConsent result = ParticipantUtils.convert(dto);
        assertTrue(result.accepted());
        assertEquals("Device#123456...890123", result.deviceId());
        assertEquals("md5hash", result.consentMd5());
        assertEquals(1, result.observationConsents().size());
        assertEquals(2, result.observationConsents().get(0).observationId());
    }
}
