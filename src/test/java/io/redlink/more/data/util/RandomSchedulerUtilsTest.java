/*
 * Copyright LBI-DHP and/or licensed to LBI-DHP under one or more
 * contributor license agreements (LBI-DHP: Ludwig Boltzmann Institute
 * for Digital Health and Prevention -- A research institute of the
 * Ludwig Boltzmann Gesellschaft, Österreichische Vereinigung zur
 * Förderung der wissenschaftlichen Forschung).
 * Licensed under the Elastic License 2.0.
 */
package io.redlink.more.data.util;

import io.redlink.more.data.model.scheduler.Duration;
import io.redlink.more.data.model.scheduler.Event;
import io.redlink.more.data.model.scheduler.Randomization;
import io.redlink.more.data.model.scheduler.RecurrenceRule;
import io.redlink.more.data.model.scheduler.RelativeDate;
import io.redlink.more.data.model.scheduler.RelativeEvent;
import io.redlink.more.data.model.scheduler.RelativeRecurrenceRule;
import io.redlink.more.data.model.scheduler.ScheduleEvent;
import org.apache.commons.lang3.Range;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RandomSchedulerUtilsTest {

    @Test
    void testGenerateSeedFromSchedule_NullSchedule() {
        Long seed = RandomSchedulerUtils.generateSeedFromSchedule(null);
        assertNull(seed, "Seed should be null");
    }

    @Test
    void testGenerateSeedFromSchedule_NullRandomization() {
        Event event = new Event()
                .setDateStart(Instant.parse("2024-01-01T10:00:00Z"))
                .setDateEnd(Instant.parse("2024-01-01T12:00:00Z"))
                .setRandomization(null);

        Long seed = RandomSchedulerUtils.generateSeedFromSchedule(event);
        assertNull(seed, "Seed should null");
    }

    @Test
    void testGenerateSeedFromSchedule_InactiveRandomization() {
        Event event = new Event()
                .setDateStart(Instant.parse("2024-01-01T10:00:00Z"))
                .setDateEnd(Instant.parse("2024-01-01T12:00:00Z"))
                .setRandomization(new Randomization(false, 60));

        Long seed = RandomSchedulerUtils.generateSeedFromSchedule(event);
        assertNull(seed, "Seed should be null when randomization state is false");
    }

    @Test
    void testGenerateSeedFromSchedule_SimpleEvent() {
        Event event = new Event()
                .setDateStart(Instant.parse("2024-01-01T10:00:00Z"))
                .setDateEnd(Instant.parse("2024-01-01T12:00:00Z"))
                .setRandomization(new Randomization(true, 60));

        long seed = RandomSchedulerUtils.generateSeedFromSchedule(event);
        assertNotEquals(0, seed, "Seed should not be 0 for valid event with active randomization");
        assertEquals(-42358863585069593L, seed);
    }

    @Test
    void testGenerateSeedFromSchedule_EventWithRecurrenceRule() {
        RecurrenceRule rrule = new RecurrenceRule()
                .setFreq("DAILY")
                .setInterval(1)
                .setCount(10);

        Event event = new Event()
                .setDateStart(Instant.parse("2024-01-01T10:00:00Z"))
                .setDateEnd(Instant.parse("2024-01-01T12:00:00Z"))
                .setRRule(rrule)
                .setRandomization(new Randomization(true, 60));

        long seed = RandomSchedulerUtils.generateSeedFromSchedule(event);
        assertNotEquals(0, seed, "Seed should not be 0 for event with recurrence rule");
        assertEquals(295789499573821007L, seed);
    }

    @Test
    void testGenerateSeedFromSchedule_EventWithComplexRecurrenceRule() {
        RecurrenceRule rrule = new RecurrenceRule()
                .setFreq("WEEKLY")
                .setInterval(2)
                .setCount(5)
                .setUntil(Instant.parse("2024-12-31T23:59:59Z"))
                .setByDay(Arrays.asList("MO", "WE", "FR"))
                .setByMonth(6)
                .setByMonthDay(15)
                .setBySetPos(1);

        Event event = new Event()
                .setDateStart(Instant.parse("2024-01-01T10:00:00Z"))
                .setDateEnd(Instant.parse("2024-01-01T12:00:00Z"))
                .setRRule(rrule)
                .setRandomization(new Randomization(true, 120));

        long seed = RandomSchedulerUtils.generateSeedFromSchedule(event);
        assertNotEquals(0, seed, "Seed should not be 0 for event with complex recurrence rule");
        assertEquals(-7121760988474980363L, seed);
    }

    @Test
    void testGenerateSeedFromSchedule_RelativeEvent() {
        RelativeDate start = new RelativeDate()
                .setTime(LocalTime.of(9, 0))
                .setOffset(new Duration().setValue(1).setUnit(Duration.Unit.DAY));

        RelativeDate end = new RelativeDate()
                .setTime(LocalTime.of(17, 0))
                .setOffset(new Duration().setValue(1).setUnit(Duration.Unit.DAY));

        RelativeEvent event = new RelativeEvent()
                .setDtstart(start)
                .setDtend(end)
                .setRandomization(new Randomization(true, 60));

        long seed = RandomSchedulerUtils.generateSeedFromSchedule(event);
        assertNotEquals(0, seed, "Seed should not be 0 for valid relative event");
        assertEquals(7730631974139984422L, seed);
    }

    @Test
    void testGenerateSeedFromSchedule_RelativeEventWithRecurrenceRule() {
        RelativeDate start = new RelativeDate()
                .setTime(LocalTime.of(9, 0))
                .setOffset(new Duration().setValue(0).setUnit(Duration.Unit.DAY));

        RelativeDate end = new RelativeDate()
                .setTime(LocalTime.of(17, 0))
                .setOffset(new Duration().setValue(0).setUnit(Duration.Unit.DAY));

        RelativeRecurrenceRule rrrule = new RelativeRecurrenceRule()
                .setFrequency(new Duration().setValue(1).setUnit(Duration.Unit.DAY))
                .setEndAfter(new Duration().setValue(7).setUnit(Duration.Unit.DAY));

        RelativeEvent event = new RelativeEvent()
                .setDtstart(start)
                .setDtend(end)
                .setRrrule(rrrule)
                .setRandomization(new Randomization(true, 90));

        long seed = RandomSchedulerUtils.generateSeedFromSchedule(event);
        assertNotEquals(0, seed, "Seed should not be 0 for relative event with recurrence rule");
        assertEquals(8838028885358937873L, seed);
    }

    @Test
    void testGenerateSeedFromSchedule_Deterministic() {
        Event event1 = new Event()
                .setDateStart(Instant.parse("2024-01-01T10:00:00Z"))
                .setDateEnd(Instant.parse("2024-01-01T12:00:00Z"))
                .setRandomization(new Randomization(true, 60));

        Event event2 = new Event()
                .setDateStart(Instant.parse("2024-01-01T10:00:00Z"))
                .setDateEnd(Instant.parse("2024-01-01T12:00:00Z"))
                .setRandomization(new Randomization(true, 60));

        long seed1 = RandomSchedulerUtils.generateSeedFromSchedule(event1);
        long seed2 = RandomSchedulerUtils.generateSeedFromSchedule(event2);

        assertEquals(seed1, seed2, "Same event configuration should produce the same seed");
        assertEquals(-42358863585069593L, seed1);
        assertEquals(-42358863585069593L, seed2);
    }

    @Test
    void testGenerateSeedFromSchedule_DifferentInputsDifferentSeeds() {
        Event event1 = new Event()
                .setDateStart(Instant.parse("2024-01-01T10:00:00Z"))
                .setDateEnd(Instant.parse("2024-01-01T12:00:00Z"))
                .setRandomization(new Randomization(true, 60));

        Event event2 = new Event()
                .setDateStart(Instant.parse("2024-01-02T10:00:00Z"))
                .setDateEnd(Instant.parse("2024-01-02T12:00:00Z"))
                .setRandomization(new Randomization(true, 60));

        long seed1 = RandomSchedulerUtils.generateSeedFromSchedule(event1);
        long seed2 = RandomSchedulerUtils.generateSeedFromSchedule(event2);

        assertNotEquals(seed1, seed2, "Different event configurations should produce different seeds");
    }

    @Test
    void testGenerateSeedFromSchedule_DifferentDurationsDifferentSeeds() {
        Event event1 = new Event()
                .setDateStart(Instant.parse("2024-01-01T10:00:00Z"))
                .setDateEnd(Instant.parse("2024-01-01T12:00:00Z"))
                .setRandomization(new Randomization(true, 60));

        Event event2 = new Event()
                .setDateStart(Instant.parse("2024-01-01T10:00:00Z"))
                .setDateEnd(Instant.parse("2024-01-01T12:00:00Z"))
                .setRandomization(new Randomization(true, 120));

        long seed1 = RandomSchedulerUtils.generateSeedFromSchedule(event1);
        long seed2 = RandomSchedulerUtils.generateSeedFromSchedule(event2);

        assertNotEquals(seed1, seed2, "Different randomization durations should produce different seeds");
    }

    @Test
    void testGenerateSeedFromSchedule_EventWithNullDates() {
        Event event = new Event()
                .setDateStart(null)
                .setDateEnd(null)
                .setRandomization(new Randomization(true, 60));

        long seed = RandomSchedulerUtils.generateSeedFromSchedule(event);
        assertNotEquals(0, seed, "Seed should still be generated even with null dates");
        assertEquals(9172556794633044776L, seed);
    }

    @Test
    void testGenerateSeedFromSchedule_EventWithPartialRecurrenceRule() {
        RecurrenceRule rrule = new RecurrenceRule()
                .setFreq("DAILY");

        Event event = new Event()
                .setDateStart(Instant.parse("2024-01-01T10:00:00Z"))
                .setDateEnd(Instant.parse("2024-01-01T12:00:00Z"))
                .setRRule(rrule)
                .setRandomization(new Randomization(true, 60));

        long seed = RandomSchedulerUtils.generateSeedFromSchedule(event);
        assertNotEquals(0, seed, "Seed should be generated with partial recurrence rule");
        assertEquals(-2975773379970995530L, seed);
    }

    @Test
    void testGenerateSeedFromSchedule_EventWithEmptyByDayList() {
        RecurrenceRule rrule = new RecurrenceRule()
                .setFreq("WEEKLY")
                .setByDay(Collections.emptyList());

        Event event = new Event()
                .setDateStart(Instant.parse("2024-01-01T10:00:00Z"))
                .setDateEnd(Instant.parse("2024-01-01T12:00:00Z"))
                .setRRule(rrule)
                .setRandomization(new Randomization(true, 60));

        long seed = RandomSchedulerUtils.generateSeedFromSchedule(event);
        assertNotEquals(0, seed, "Seed should be generated with empty byDay list");
        assertEquals(-5438816475475765172L, seed);
    }

    @Test
    void testGenerateSeedFromSchedule_EventWithByDayList() {
        RecurrenceRule rrule1 = new RecurrenceRule()
                .setFreq("WEEKLY")
                .setByDay(Arrays.asList("MO", "WE"));

        RecurrenceRule rrule2 = new RecurrenceRule()
                .setFreq("WEEKLY")
                .setByDay(Arrays.asList("TU", "TH"));

        Event event1 = new Event()
                .setDateStart(Instant.parse("2024-01-01T10:00:00Z"))
                .setDateEnd(Instant.parse("2024-01-01T12:00:00Z"))
                .setRRule(rrule1)
                .setRandomization(new Randomization(true, 60));

        Event event2 = new Event()
                .setDateStart(Instant.parse("2024-01-01T10:00:00Z"))
                .setDateEnd(Instant.parse("2024-01-01T12:00:00Z"))
                .setRRule(rrule2)
                .setRandomization(new Randomization(true, 60));

        long seed1 = RandomSchedulerUtils.generateSeedFromSchedule(event1);
        long seed2 = RandomSchedulerUtils.generateSeedFromSchedule(event2);

        assertNotEquals(seed1, seed2, "Different byDay lists should produce different seeds");
    }

    @Test
    void testGenerateSeedFromSchedule_RelativeEventWithNullFields() {
        RelativeEvent event = new RelativeEvent()
                .setDtstart(null)
                .setDtend(null)
                .setRrrule(null)
                .setRandomization(new Randomization(true, 60));

        long seed = RandomSchedulerUtils.generateSeedFromSchedule(event);
        assertNotEquals(0, seed, "Seed should still be generated for relative event with null fields");
        assertEquals(-3276571355333199820L, seed);
    }

    @Test
    void testGenerateSeedFromSchedule_RelativeEventWithNullTime() {
        RelativeDate start = new RelativeDate()
                .setTime(null)
                .setOffset(new Duration().setValue(1).setUnit(Duration.Unit.DAY));

        RelativeEvent event = new RelativeEvent()
                .setDtstart(start)
                .setDtend(null)
                .setRandomization(new Randomization(true, 60));

        long seed = RandomSchedulerUtils.generateSeedFromSchedule(event);
        assertNotEquals(0, seed, "Seed should be generated even with null time in RelativeDate");
        assertEquals(6386706127595477336L, seed);
    }

    @Test
    void testGenerateSeedFromSchedule_RelativeEventDeterministic() {
        RelativeDate start1 = new RelativeDate()
                .setTime(LocalTime.of(10, 30))
                .setOffset(new Duration().setValue(2).setUnit(Duration.Unit.HOUR));

        RelativeDate end1 = new RelativeDate()
                .setTime(LocalTime.of(15, 45))
                .setOffset(new Duration().setValue(3).setUnit(Duration.Unit.HOUR));

        RelativeEvent event1 = new RelativeEvent()
                .setDtstart(start1)
                .setDtend(end1)
                .setRandomization(new Randomization(true, 90));

        RelativeDate start2 = new RelativeDate()
                .setTime(LocalTime.of(10, 30))
                .setOffset(new Duration().setValue(2).setUnit(Duration.Unit.HOUR));

        RelativeDate end2 = new RelativeDate()
                .setTime(LocalTime.of(15, 45))
                .setOffset(new Duration().setValue(3).setUnit(Duration.Unit.HOUR));

        RelativeEvent event2 = new RelativeEvent()
                .setDtstart(start2)
                .setDtend(end2)
                .setRandomization(new Randomization(true, 90));

        long seed1 = RandomSchedulerUtils.generateSeedFromSchedule(event1);
        long seed2 = RandomSchedulerUtils.generateSeedFromSchedule(event2);

        assertEquals(seed1, seed2, "Same relative event configuration should produce the same seed");
        assertEquals(4894757711342650077L, seed1);
        assertEquals(4894757711342650077L, seed2);
    }

    @Test
    void testGenerateSeedFromSchedule_RelativeEventDifferentTimes() {
        RelativeDate start1 = new RelativeDate()
                .setTime(LocalTime.of(9, 0))
                .setOffset(new Duration().setValue(1).setUnit(Duration.Unit.DAY));

        RelativeDate start2 = new RelativeDate()
                .setTime(LocalTime.of(10, 0))
                .setOffset(new Duration().setValue(1).setUnit(Duration.Unit.DAY));

        RelativeEvent event1 = new RelativeEvent()
                .setDtstart(start1)
                .setRandomization(new Randomization(true, 60));

        RelativeEvent event2 = new RelativeEvent()
                .setDtstart(start2)
                .setRandomization(new Randomization(true, 60));

        long seed1 = RandomSchedulerUtils.generateSeedFromSchedule(event1);
        long seed2 = RandomSchedulerUtils.generateSeedFromSchedule(event2);

        assertNotEquals(seed1, seed2, "Different times should produce different seeds");
    }

    @Test
    void testGenerateSeedFromSchedule_RelativeEventDifferentOffsets() {
        RelativeDate start1 = new RelativeDate()
                .setTime(LocalTime.of(9, 0))
                .setOffset(new Duration().setValue(1).setUnit(Duration.Unit.DAY));

        RelativeDate start2 = new RelativeDate()
                .setTime(LocalTime.of(9, 0))
                .setOffset(new Duration().setValue(2).setUnit(Duration.Unit.DAY));

        RelativeEvent event1 = new RelativeEvent()
                .setDtstart(start1)
                .setRandomization(new Randomization(true, 60));

        RelativeEvent event2 = new RelativeEvent()
                .setDtstart(start2)
                .setRandomization(new Randomization(true, 60));

        long seed1 = RandomSchedulerUtils.generateSeedFromSchedule(event1);
        long seed2 = RandomSchedulerUtils.generateSeedFromSchedule(event2);

        assertNotEquals(seed1, seed2, "Different offsets should produce different seeds");
    }

    @Test
    void testGenerateSeedFromSchedule_UnknownScheduleType() {
        ScheduleEvent unknownSchedule = new ScheduleEvent() {
            @Override
            public String getType() {
                return "UnknownType";
            }

            @Override
            public Randomization getRandomization() {
                return new Randomization(true, 60);
            }

            @Override
            public ScheduleEvent setRandomization(Randomization randomization) {
                return this;
            }
        };

        Long seed = RandomSchedulerUtils.generateSeedFromSchedule(unknownSchedule);
        assertNull(seed, "Seed should be null for unknown schedule type");
    }

    // Tests for parseScheduleWithSeed function

    @Test
    void testParseScheduleWithSeed_NullSeed_ReturnsEmpty() {
        List<Range<Instant>> ranges = List.of(
                Range.of(Instant.parse("2024-01-01T00:00:00Z"), Instant.parse("2024-01-01T01:00:00Z"))
        );

        List<Range<Instant>> result = RandomSchedulerUtils.parseScheduleWithSeed(null, ranges, 600L);
        assertEquals(Collections.emptyList(), result);
    }

    @Test
    void testParseScheduleWithSeed_NullRanges_ReturnsEmpty() {
        List<Range<Instant>> result = RandomSchedulerUtils.parseScheduleWithSeed(123L, null, 600L);
        assertEquals(Collections.emptyList(), result);
    }

    @Test
    void testParseScheduleWithSeed_EmptyRanges_ReturnsEmpty() {
        List<Range<Instant>> result = RandomSchedulerUtils.parseScheduleWithSeed(123L, Collections.emptyList(), 600L);
        assertEquals(Collections.emptyList(), result);
    }

    @Test
    void testParseScheduleWithSeed_DeterministicSameInputs() {
        List<Range<Instant>> ranges = List.of(
                Range.of(Instant.parse("2024-01-01T00:00:00Z"), Instant.parse("2024-01-01T01:00:00Z")),
                Range.of(Instant.parse("2024-01-02T10:00:00Z"), Instant.parse("2024-01-02T12:00:00Z"))
        );

        long durationSeconds = 15 * 60; // 15 minutes
        Long seed = 42L;

        List<Range<Instant>> r1 = RandomSchedulerUtils.parseScheduleWithSeed(seed, ranges, durationSeconds);
        List<Range<Instant>> r2 = RandomSchedulerUtils.parseScheduleWithSeed(seed, ranges, durationSeconds);

        assertEquals(r1, r2, "Same seed + same windows should produce identical ranges");
    }

    @Test
    void testParseScheduleWithSeed_OutputWithinWindowsAndCorrectDuration() {
        Range<Instant> w1 = Range.of(Instant.parse("2024-01-01T00:00:00Z"), Instant.parse("2024-01-01T01:00:00Z"));
        Range<Instant> w2 = Range.of(Instant.parse("2024-01-02T10:00:00Z"), Instant.parse("2024-01-02T12:00:00Z"));
        List<Range<Instant>> ranges = List.of(w1, w2);

        long durationSeconds = 10 * 60; // 10 minutes
        List<Range<Instant>> result = RandomSchedulerUtils.parseScheduleWithSeed(123L, ranges, durationSeconds);

        assertEquals(2, result.size(), "Should return one deterministic range per input window");

        assertRangeWithinWindowWithDuration(w1, result.get(0), durationSeconds);
        assertRangeWithinWindowWithDuration(w2, result.get(1), durationSeconds);
    }

    @Test
    void testParseScheduleWithSeed_DurationDoesNotFit_FiltersOutRange() {
        // Window is only 5 minutes, duration is 10 minutes -> should be filtered out
        Range<Instant> smallWindow = Range.of(Instant.parse("2024-01-01T00:00:00Z"), Instant.parse("2024-01-01T00:05:00Z"));
        List<Range<Instant>> result = RandomSchedulerUtils.parseScheduleWithSeed(999L, List.of(smallWindow), 10 * 60L);
        assertEquals(Collections.emptyList(), result);
    }

    @Test
    void testParseScheduleWithSeed_OccurrenceIndexInfluencesResult() {
        // Two identical windows must not yield identical ranges because occurrenceIndex is part of the hash
        Range<Instant> window = Range.of(Instant.parse("2024-01-01T00:00:00Z"), Instant.parse("2024-01-01T01:00:00Z"));
        List<Range<Instant>> ranges = List.of(window, window);

        long durationSeconds = 10 * 60;
        List<Range<Instant>> result = RandomSchedulerUtils.parseScheduleWithSeed(123L, ranges, durationSeconds);

        assertEquals(2, result.size());
        assertNotEquals(result.get(0), result.get(1), "Two identical windows should produce different offsets due to occurrenceIndex");
        assertRangeWithinWindowWithDuration(window, result.get(0), durationSeconds);
        assertRangeWithinWindowWithDuration(window, result.get(1), durationSeconds);
    }

    @Test
    void testParseScheduleWithSeed_DifferentSeedsUsuallyDifferentOutput() {
        Range<Instant> window = Range.of(Instant.parse("2024-01-01T00:00:00Z"), Instant.parse("2024-01-01T01:00:00Z"));
        List<Range<Instant>> ranges = List.of(window);

        long durationSeconds = 10 * 60;
        List<Range<Instant>> r1 = RandomSchedulerUtils.parseScheduleWithSeed(1L, ranges, durationSeconds);
        List<Range<Instant>> r2 = RandomSchedulerUtils.parseScheduleWithSeed(2L, ranges, durationSeconds);

        // Extremely unlikely to collide; this is a regression guard for seed usage.
        assertNotEquals(r1, r2, "Different seeds should (almost always) produce different deterministic ranges");
    }

    private static void assertRangeWithinWindowWithDuration(Range<Instant> window, Range<Instant> chosen, long durationSeconds) {
        Instant wStart = window.getMinimum();
        Instant wEnd = window.getMaximum();

        Instant cStart = chosen.getMinimum();
        Instant cEnd = chosen.getMaximum();

        assertEquals(durationSeconds, cEnd.getEpochSecond() - cStart.getEpochSecond(), "Chosen range must match duration");
        // chosen.start >= window.start
        assertEquals(true, !cStart.isBefore(wStart), "Chosen start must not be before window start");
        // chosen.end <= window.end
        assertEquals(true, !cEnd.isAfter(wEnd), "Chosen end must not be after window end");
    }


}
