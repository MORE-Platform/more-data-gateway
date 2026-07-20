package io.redlink.more.data.model.garmin.transformation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GarminSleepData(
        LocalDate calendarDate,
        Long totalNapDurationInSeconds,
        Long unmeasurableSleepInSeconds,
        Long deepSleepDurationInSeconds,
        Long lightSleepDurationInSeconds,
        Long remSleepInSeconds,
        Long awakeDurationInSeconds,
        String validation,
        Map<String, Integer> timeOffsetSleepSpo2,
        Map<String, List<SleepLevelSegment>> sleepLevelsMap,
        Integer overallSleepScoreValue,
        SleepScore overallSleepScore,
        SleepScoreBreakdown sleepScores,
        List<NapData> naps
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SleepLevelSegment(
            Long startTimeInSeconds,
            Long endTimeInSeconds
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record NapData(
            Long napStartTimeInSeconds,
            Integer napOffsetInSeconds,
            Long napDurationInSeconds,
            String napValidation
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SleepScore(
            Integer value,
            String qualifierKey
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SleepScoreBreakdown(
            SleepScoreQualifier totalDuration,
            SleepScoreQualifier stress,
            SleepScoreQualifier awakeCount,
            SleepScoreQualifier remPercentage,
            SleepScoreQualifier restlessness,
            SleepScoreQualifier lightPercentage,
            SleepScoreQualifier deepPercentage
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SleepScoreQualifier(
            String qualifierKey
    ) {
    }
}