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
