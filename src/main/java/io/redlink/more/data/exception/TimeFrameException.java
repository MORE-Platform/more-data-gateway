/*
 * Copyright LBI-DHP and/or licensed to LBI-DHP under one or more
 * contributor license agreements (LBI-DHP: Ludwig Boltzmann Institute
 * for Digital Health and Prevention -- A research institute of the
 * Ludwig Boltzmann Gesellschaft, Oesterreichische Vereinigung zur
 * Foerderung der wissenschaftlichen Forschung).
 * Licensed under the Apache 2.0 license (see https://www.apache.org/licenses/LICENSE-2.0).
 */
package io.redlink.more.data.exception;

import io.redlink.more.data.model.scheduler.Interval;

import java.util.List;
import java.util.stream.Collectors;

public class TimeFrameException extends BadRequestException {
    public TimeFrameException(String cause) {
        super(cause);
    }

    public static TimeFrameException InvalidDataPointInterval(String dataBulkId, List<Interval> intervalList) {
        return new TimeFrameException(
                String.format(
                        "The provided data bulk with ID '%s' contains timestamps that are not within the valid intervals: [%s]",
                        dataBulkId,
                        intervalList
                                .stream()
                                .map(Interval::toString)
                                .collect(Collectors.joining(", "))
                )
        );
    }
}
