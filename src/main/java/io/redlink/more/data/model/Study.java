package io.redlink.more.data.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record Study(
        long studyId,
        String title,
        boolean active,
        String participantInfo,
        String consentInfo,
        Contact contact,
        LocalDate startDate,
        LocalDate endDate,
        List<Observation> observations,
        Instant created,
        Instant modified
) {
}
