package io.redlink.more.data.util;

import io.redlink.more.data.model.ParticipantConsent;
import org.apache.commons.lang3.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class ParticipantUtils {
    public static ParticipantConsent convert(io.redlink.more.data.api.participant.v1.model.StudyConsentDTO dto) {
        return new ParticipantConsent(
                dto.getConsent(),
                obfuscate(dto.getDeviceId()),
                dto.getConsentInfoMD5(),
                null,
                convertParticipantObservations(dto.getObservations())
        );
    }

    public static ParticipantConsent convert(io.redlink.more.data.api.app.v1.model.StudyConsentDTO dto) {
        return new ParticipantConsent(
                dto.getConsent(),
                obfuscate(dto.getDeviceId()),
                dto.getConsentInfoMD5(),
                null,
                convertAppObservations(dto.getObservations())
        );
    }

    /**
     * Device-ID has the format "[MODEL]#[SERIAL], we obfuscate to [MODEL]#[SERIAL:0:6]...[SERIAL:-6:-0]
     */
    static String obfuscate(String string) {
        final String unknown = "unknown";
        if (StringUtils.isBlank(string)) return unknown;

        final int keepChars = 6;
        final int delim = string.indexOf('#');
        if (delim < 0) {
            // No delimiter found, handle as whole string
            if (string.length() <= keepChars * 2) {
                return string;
            }
            return "%s...%s".formatted(
                    StringUtils.left(string, keepChars),
                    StringUtils.right(string, keepChars)
            );
        }

        String model = StringUtils.left(string, delim);
        String serial = StringUtils.substring(string, delim + 1);

        if (serial.length() <= keepChars * 2) {
            return "%s#%s".formatted(
                    StringUtils.defaultIfEmpty(model, unknown),
                    serial
            );
        }

        return "%s#%s...%s".formatted(
                StringUtils.defaultIfEmpty(model, unknown),
                StringUtils.left(serial, keepChars),
                StringUtils.right(serial, keepChars)
        );
    }

    private static List<ParticipantConsent.ObservationConsent> convertAppObservations(List<io.redlink.more.data.api.app.v1.model.ObservationConsentDTO> observations) {
        return Optional.ofNullable(observations).orElse(Collections.emptyList()).stream()
                .map(ParticipantUtils::convert)
                .toList();
    }

    private static ParticipantConsent.ObservationConsent convert(io.redlink.more.data.api.app.v1.model.ObservationConsentDTO observation) {
        return new ParticipantConsent.ObservationConsent(
                Integer.parseInt(observation.getObservationId()),
                null
        );
    }

    private static List<ParticipantConsent.ObservationConsent> convertParticipantObservations(List<io.redlink.more.data.api.participant.v1.model.ObservationConsentDTO> observations) {
        return Optional.ofNullable(observations).orElse(Collections.emptyList()).stream()
                .map(ParticipantUtils::convert)
                .toList();
    }

    private static ParticipantConsent.ObservationConsent convert(io.redlink.more.data.api.participant.v1.model.ObservationConsentDTO observation) {
        return new ParticipantConsent.ObservationConsent(
                Integer.parseInt(observation.getObservationId()),
                null
        );
    }
}
