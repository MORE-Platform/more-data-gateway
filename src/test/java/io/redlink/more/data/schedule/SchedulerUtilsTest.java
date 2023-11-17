package io.redlink.more.data.schedule;

import io.redlink.more.data.model.scheduler.*;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class SchedulerUtilsTest {

    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Test
    @DisplayName("Parsing daily event with count and until")
    void testParseDailyEvent() {
        List<Pair<Instant, Instant>> expectedValues = new ArrayList<>();

        expectedValues.add(Pair.of(LocalDateTime.parse("2022-11-23 14:00:00", formatter).toInstant(ZoneOffset.UTC)
                ,LocalDateTime.parse("2022-11-23 16:00:00", formatter).toInstant(ZoneOffset.UTC)));
        expectedValues.add(Pair.of(LocalDateTime.parse("2022-11-24 14:00:00", formatter).toInstant(ZoneOffset.UTC),
                LocalDateTime.parse("2022-11-24 16:00:00", formatter).toInstant(ZoneOffset.UTC)));
        expectedValues.add(Pair.of(LocalDateTime.parse("2022-11-25 14:00:00", formatter).toInstant(ZoneOffset.UTC),
                LocalDateTime.parse("2022-11-25 16:00:00", formatter).toInstant(ZoneOffset.UTC)));

        Event eventCount = new Event()
                .setDateStart(LocalDateTime.parse("2022-11-23 14:00:00", formatter).toInstant(ZoneOffset.UTC))
                .setDateEnd(LocalDateTime.parse("2022-11-23 16:00:00", formatter).toInstant(ZoneOffset.UTC))
                .setRRule(new RecurrenceRule()
                        .setFreq("DAILY")
                        .setInterval(1)
                        .setCount(3));
        List<Pair<Instant, Instant>> actualValues = SchedulerUtils.parseToObservationSchedules(eventCount, Instant.now(), Instant.now());
        assertArrayEquals(Arrays.stream(expectedValues.toArray()).map(Object::toString).toArray(),
                Arrays.stream(actualValues.toArray()).map(Object::toString).toArray());

        Event eventUntil = new Event()
                .setDateStart(LocalDateTime.parse("2022-11-23 14:00:00", formatter).toInstant(ZoneOffset.UTC))
                .setDateEnd(LocalDateTime.parse("2022-11-23 16:00:00", formatter).toInstant(ZoneOffset.UTC))
                .setRRule(new RecurrenceRule()
                        .setFreq("DAILY")
                        .setInterval(1)
                        .setUntil(LocalDateTime.parse("2022-11-25 14:00:00", formatter).toInstant(ZoneOffset.UTC)));

        actualValues = SchedulerUtils.parseToObservationSchedules(eventUntil, Instant.now(), Instant.now());
        assertArrayEquals(Arrays.stream(expectedValues.toArray()).map(Object::toString).toArray(),
                Arrays.stream(actualValues.toArray()).map(Object::toString).toArray());    }

    @Test
    @DisplayName("Parsing daily event with count and until. Event duration is 30min")
    void testParseDailyEventWith30MinDuration() {
        List<Pair<Instant, Instant>> expectedValues = new ArrayList<>();

        expectedValues.add(Pair.of(LocalDateTime.parse("2022-11-23 14:00:00", formatter).toInstant(ZoneOffset.UTC)
                ,LocalDateTime.parse("2022-11-23 14:30:00", formatter).toInstant(ZoneOffset.UTC)));
        expectedValues.add(Pair.of(LocalDateTime.parse("2022-11-24 14:00:00", formatter).toInstant(ZoneOffset.UTC),
                LocalDateTime.parse("2022-11-24 14:30:00", formatter).toInstant(ZoneOffset.UTC)));
        expectedValues.add(Pair.of(LocalDateTime.parse("2022-11-25 14:00:00", formatter).toInstant(ZoneOffset.UTC),
                LocalDateTime.parse("2022-11-25 14:30:00", formatter).toInstant(ZoneOffset.UTC)));

        Event eventCount = new Event()
                .setDateStart(LocalDateTime.parse("2022-11-23 14:00:00", formatter).toInstant(ZoneOffset.UTC))
                .setDateEnd(LocalDateTime.parse("2022-11-23 14:30:00", formatter).toInstant(ZoneOffset.UTC))
                .setRRule(new RecurrenceRule()
                        .setFreq("DAILY")
                        .setInterval(1)
                        .setCount(3));
        List<Pair<Instant, Instant>> actualValues = SchedulerUtils.parseToObservationSchedules(eventCount, Instant.now(), Instant.now());
        assertArrayEquals(Arrays.stream(expectedValues.toArray()).map(Object::toString).toArray(),
                Arrays.stream(actualValues.toArray()).map(Object::toString).toArray());

        Event eventUntil = new Event()
                .setDateStart(LocalDateTime.parse("2022-11-23 14:00:00", formatter).toInstant(ZoneOffset.UTC))
                .setDateEnd(LocalDateTime.parse("2022-11-23 14:30:00", formatter).toInstant(ZoneOffset.UTC))
                .setRRule(new RecurrenceRule()
                        .setFreq("DAILY")
                        .setInterval(1)
                        .setUntil(LocalDateTime.parse("2022-11-25 14:00:00", formatter).toInstant(ZoneOffset.UTC)));

        actualValues = SchedulerUtils.parseToObservationSchedules(eventUntil, Instant.now(), Instant.now());
        assertArrayEquals(Arrays.stream(expectedValues.toArray()).map(Object::toString).toArray(),
                Arrays.stream(actualValues.toArray()).map(Object::toString).toArray());    }
    @Test
    @DisplayName("Parsing monthly event with until and byDay and bySetPos")
    void testParseMonthlyEvent() {
        List<Pair<Instant, Instant>> expectedValues = new ArrayList<>();

        expectedValues.add(Pair.of(LocalDateTime.parse("2022-12-05 14:00:00", formatter).toInstant(ZoneOffset.UTC)
                ,LocalDateTime.parse("2022-12-05 16:00:00", formatter).toInstant(ZoneOffset.UTC)));
        expectedValues.add(Pair.of(LocalDateTime.parse("2023-01-02 14:00:00", formatter).toInstant(ZoneOffset.UTC),
                LocalDateTime.parse("2023-01-02 16:00:00", formatter).toInstant(ZoneOffset.UTC)));
        expectedValues.add(Pair.of(LocalDateTime.parse("2023-02-06 14:00:00", formatter).toInstant(ZoneOffset.UTC),
                LocalDateTime.parse("2023-02-06 16:00:00", formatter).toInstant(ZoneOffset.UTC)));

        Event event = new Event()
                .setDateStart(LocalDateTime.parse("2022-11-23 14:00:00", formatter).toInstant(ZoneOffset.UTC))
                .setDateEnd(LocalDateTime.parse("2022-11-23 16:00:00", formatter).toInstant(ZoneOffset.UTC))
                .setRRule(new RecurrenceRule()
                        .setFreq("MONTHLY")
                        .setInterval(1)
                        .setByDay(List.of(new String[]{"MO"}))
                        .setBySetPos(1)
                        .setCount(3));

        List<Pair<Instant, Instant>> actualValues = SchedulerUtils.parseToObservationSchedules(event, Instant.now(), Instant.now());
        assertArrayEquals(Arrays.stream(expectedValues.toArray()).map(Object::toString).toArray(),
                Arrays.stream(actualValues.toArray()).map(Object::toString).toArray());    }

    @Test
    @DisplayName("Parsing monthly event with until and array of byDay and bySetPos")
    void testParseMonthlyEventByDays() {
        List<Pair<Instant, Instant>> expectedValues = new ArrayList<>();

        expectedValues.add(Pair.of(LocalDateTime.parse("2022-12-05 14:00:00", formatter).toInstant(ZoneOffset.UTC)
                ,LocalDateTime.parse("2022-12-05 16:00:00", formatter).toInstant(ZoneOffset.UTC)));
        expectedValues.add(Pair.of(LocalDateTime.parse("2022-12-06 14:00:00", formatter).toInstant(ZoneOffset.UTC),
                LocalDateTime.parse("2022-12-06 16:00:00", formatter).toInstant(ZoneOffset.UTC)));
        expectedValues.add(Pair.of(LocalDateTime.parse("2022-12-07 14:00:00", formatter).toInstant(ZoneOffset.UTC),
                LocalDateTime.parse("2022-12-07 16:00:00", formatter).toInstant(ZoneOffset.UTC)));

        expectedValues.add(Pair.of(LocalDateTime.parse("2023-01-02 14:00:00", formatter).toInstant(ZoneOffset.UTC)
                ,LocalDateTime.parse("2023-01-02 16:00:00", formatter).toInstant(ZoneOffset.UTC)));
        expectedValues.add(Pair.of(LocalDateTime.parse("2023-01-03 14:00:00", formatter).toInstant(ZoneOffset.UTC),
                LocalDateTime.parse("2023-01-03 16:00:00", formatter).toInstant(ZoneOffset.UTC)));
        expectedValues.add(Pair.of(LocalDateTime.parse("2023-01-04 14:00:00", formatter).toInstant(ZoneOffset.UTC),
                LocalDateTime.parse("2023-01-04 16:00:00", formatter).toInstant(ZoneOffset.UTC)));

        expectedValues.add(Pair.of(LocalDateTime.parse("2023-02-01 14:00:00", formatter).toInstant(ZoneOffset.UTC)
                ,LocalDateTime.parse("2023-02-01 16:00:00", formatter).toInstant(ZoneOffset.UTC)));
        expectedValues.add(Pair.of(LocalDateTime.parse("2023-02-06 14:00:00", formatter).toInstant(ZoneOffset.UTC),
                LocalDateTime.parse("2023-02-06 16:00:00", formatter).toInstant(ZoneOffset.UTC)));
        expectedValues.add(Pair.of(LocalDateTime.parse("2023-02-07 14:00:00", formatter).toInstant(ZoneOffset.UTC),
                LocalDateTime.parse("2023-02-07 16:00:00", formatter).toInstant(ZoneOffset.UTC)));

        Event event = new Event()
                .setDateStart(LocalDateTime.parse("2022-12-05 14:00:00", formatter).toInstant(ZoneOffset.UTC))
                .setDateEnd(LocalDateTime.parse("2022-12-05 16:00:00", formatter).toInstant(ZoneOffset.UTC))
                .setRRule(new RecurrenceRule()
                        .setFreq("MONTHLY")
                        .setInterval(1)
                        .setByDay(List.of(new String[]{"MO", "TU", "WE"}))
                        .setBySetPos(1)
                        .setCount(9));
        List<Pair<Instant, Instant>> actualValues = SchedulerUtils.parseToObservationSchedules(event, Instant.now(), Instant.now());
        assertArrayEquals(Arrays.stream(expectedValues.toArray()).map(Object::toString).toArray(),
                Arrays.stream(actualValues.toArray()).map(Object::toString).toArray());    }

    @Test
    @DisplayName("Parsing weekly event with count")
    void testParseWeeklyEvent() {
        List<Pair<Instant, Instant>> expectedValues = new ArrayList<>();

        expectedValues.add(Pair.of(LocalDateTime.parse("2022-11-23 14:00:00", formatter).toInstant(ZoneOffset.UTC)
                ,LocalDateTime.parse("2022-11-23 16:00:00", formatter).toInstant(ZoneOffset.UTC)));
        expectedValues.add(Pair.of(LocalDateTime.parse("2022-11-30 14:00:00", formatter).toInstant(ZoneOffset.UTC),
                LocalDateTime.parse("2022-11-30 16:00:00", formatter).toInstant(ZoneOffset.UTC)));
        expectedValues.add(Pair.of(LocalDateTime.parse("2022-12-07 14:00:00", formatter).toInstant(ZoneOffset.UTC),
                LocalDateTime.parse("2022-12-07 16:00:00", formatter).toInstant(ZoneOffset.UTC)));

        Event event = new Event()
                .setDateStart(LocalDateTime.parse("2022-11-23 14:00:00", formatter).toInstant(ZoneOffset.UTC))
                .setDateEnd(LocalDateTime.parse("2022-11-23 16:00:00", formatter).toInstant(ZoneOffset.UTC))
                .setRRule(new RecurrenceRule()
                        .setFreq("WEEKLY")
                        .setInterval(1)
                        .setByDay(List.of(new String[]{"WE"}))
                        .setCount(3));
        List<Pair<Instant, Instant>> actualValues = SchedulerUtils.parseToObservationSchedules(event, Instant.now(), Instant.now());
        assertArrayEquals(Arrays.stream(expectedValues.toArray()).map(Object::toString).toArray(),
                Arrays.stream(actualValues.toArray()).map(Object::toString).toArray());    }

    @Test
    @DisplayName("Parsing yearly event with count")
    void testParseYearlyEvent() {
        List<Pair<Instant, Instant>> expectedValues = new ArrayList<>();

        expectedValues.add(Pair.of(LocalDateTime.parse("2022-12-05 14:00:00", formatter).toInstant(ZoneOffset.UTC)
                ,LocalDateTime.parse("2022-12-05 16:00:00", formatter).toInstant(ZoneOffset.UTC)));
        expectedValues.add(Pair.of(LocalDateTime.parse("2023-12-05 14:00:00", formatter).toInstant(ZoneOffset.UTC),
                LocalDateTime.parse("2023-12-05 16:00:00", formatter).toInstant(ZoneOffset.UTC)));
        expectedValues.add(Pair.of(LocalDateTime.parse("2024-12-05 14:00:00", formatter).toInstant(ZoneOffset.UTC),
                LocalDateTime.parse("2024-12-05 16:00:00", formatter).toInstant(ZoneOffset.UTC)));

        Event event = new Event()
                .setDateStart(LocalDateTime.parse("2022-12-05 14:00:00", formatter).toInstant(ZoneOffset.UTC))
                .setDateEnd(LocalDateTime.parse("2022-12-05 16:00:00", formatter).toInstant(ZoneOffset.UTC))
                .setRRule(new RecurrenceRule()
                        .setFreq("YEARLY")
                        .setInterval(1)
                        .setByMonthDay(5)
                        .setByMonth(12)
                        .setCount(3));
        List<Pair<Instant, Instant>> actualValues = SchedulerUtils.parseToObservationSchedules(event, Instant.now(), Instant.now());
        assertArrayEquals(Arrays.stream(expectedValues.toArray()).map(Object::toString).toArray(),
                Arrays.stream(actualValues.toArray()).map(Object::toString).toArray());    }

    @Test
    @DisplayName("Parsing yearly event with count and bySetPos")
    void testParseYearlyEventBySetPos() {
        List<Pair<Instant, Instant>> expectedValues = new ArrayList<>();

        expectedValues.add(Pair.of(LocalDateTime.parse("2022-12-05 14:00:00", formatter).toInstant(ZoneOffset.UTC)
                ,LocalDateTime.parse("2022-12-05 16:00:00", formatter).toInstant(ZoneOffset.UTC)));
        expectedValues.add(Pair.of(LocalDateTime.parse("2023-12-04 14:00:00", formatter).toInstant(ZoneOffset.UTC),
                LocalDateTime.parse("2023-12-04 16:00:00", formatter).toInstant(ZoneOffset.UTC)));
        expectedValues.add(Pair.of(LocalDateTime.parse("2024-12-02 14:00:00", formatter).toInstant(ZoneOffset.UTC),
                LocalDateTime.parse("2024-12-02 16:00:00", formatter).toInstant(ZoneOffset.UTC)));

        Event event = new Event()
                .setDateStart(LocalDateTime.parse("2022-12-05 14:00:00", formatter).toInstant(ZoneOffset.UTC))
                .setDateEnd(LocalDateTime.parse("2022-12-05 16:00:00", formatter).toInstant(ZoneOffset.UTC))
                .setRRule(new RecurrenceRule()
                        .setFreq("YEARLY")
                        .setInterval(1)
                        .setBySetPos(1)
                        .setByDay(List.of(new String[]{"MO"}))
                        .setByMonth(12)
                        .setCount(3));
        List<Pair<Instant, Instant>> actualValues = SchedulerUtils.parseToObservationSchedules(event, Instant.now(), Instant.now());
        assertArrayEquals(Arrays.stream(expectedValues.toArray()).map(Object::toString).toArray(),
                Arrays.stream(actualValues.toArray()).map(Object::toString).toArray());    }

    @Test
    @DisplayName("Parsing yearly event with count and bySetPos and byDays")
    void testParseYearlyEventBySetPosAndByDays() {
        List<Pair<Instant, Instant>> expectedValues = new ArrayList<>();

        expectedValues.add(Pair.of(LocalDateTime.parse("2022-12-05 14:00:00", formatter).toInstant(ZoneOffset.UTC)
                ,LocalDateTime.parse("2022-12-05 16:00:00", formatter).toInstant(ZoneOffset.UTC)));
        expectedValues.add(Pair.of(LocalDateTime.parse("2022-12-06 14:00:00", formatter).toInstant(ZoneOffset.UTC),
                LocalDateTime.parse("2022-12-06 16:00:00", formatter).toInstant(ZoneOffset.UTC)));

        expectedValues.add(Pair.of(LocalDateTime.parse("2023-12-04 14:00:00", formatter).toInstant(ZoneOffset.UTC)
                ,LocalDateTime.parse("2023-12-04 16:00:00", formatter).toInstant(ZoneOffset.UTC)));
        expectedValues.add(Pair.of(LocalDateTime.parse("2023-12-05 14:00:00", formatter).toInstant(ZoneOffset.UTC),
                LocalDateTime.parse("2023-12-05 16:00:00", formatter).toInstant(ZoneOffset.UTC)));

        expectedValues.add(Pair.of(LocalDateTime.parse("2024-12-02 14:00:00", formatter).toInstant(ZoneOffset.UTC)
                ,LocalDateTime.parse("2024-12-02 16:00:00", formatter).toInstant(ZoneOffset.UTC)));
        expectedValues.add(Pair.of(LocalDateTime.parse("2024-12-03 14:00:00", formatter).toInstant(ZoneOffset.UTC),
                LocalDateTime.parse("2024-12-03 16:00:00", formatter).toInstant(ZoneOffset.UTC)));

        Event event = new Event()
                .setDateStart(LocalDateTime.parse("2022-12-05 14:00:00", formatter).toInstant(ZoneOffset.UTC))
                .setDateEnd(LocalDateTime.parse("2022-12-05 16:00:00", formatter).toInstant(ZoneOffset.UTC))
                .setRRule(new RecurrenceRule()
                        .setFreq("YEARLY")
                        .setInterval(1)
                        .setBySetPos(1)
                        .setByDay(List.of(new String[]{"MO", "TU"}))
                        .setByMonth(12)
                        .setCount(6));
        List<Pair<Instant, Instant>> actualValues = SchedulerUtils.parseToObservationSchedules(event, Instant.now(), Instant.now());
        assertArrayEquals(Arrays.stream(expectedValues.toArray()).map(Object::toString).toArray(),
                Arrays.stream(actualValues.toArray()).map(Object::toString).toArray());
    }

    @Test
    @DisplayName("Parsing hourly event with until")
    void testParseHourlyEvent() {
        List<Pair<Instant, Instant>> expectedValues = new ArrayList<>();

        expectedValues.add(Pair.of(LocalDateTime.parse("2022-12-05 15:00:00", formatter).toInstant(ZoneOffset.UTC)
                ,LocalDateTime.parse("2022-12-05 16:00:00", formatter).toInstant(ZoneOffset.UTC)));
        expectedValues.add(Pair.of(LocalDateTime.parse("2022-12-05 17:00:00", formatter).toInstant(ZoneOffset.UTC),
                LocalDateTime.parse("2022-12-05 18:00:00", formatter).toInstant(ZoneOffset.UTC)));
        expectedValues.add(Pair.of(LocalDateTime.parse("2022-12-05 19:00:00", formatter).toInstant(ZoneOffset.UTC),
                LocalDateTime.parse("2022-12-05 20:00:00", formatter).toInstant(ZoneOffset.UTC)));

        Event event = new Event()
                .setDateStart(LocalDateTime.parse("2022-12-05 15:00:00", formatter).toInstant(ZoneOffset.UTC))
                .setDateEnd(LocalDateTime.parse("2022-12-05 16:00:00", formatter).toInstant(ZoneOffset.UTC))
                .setRRule(new RecurrenceRule()
                        .setFreq("HOURLY")
                        .setInterval(2)
                        .setUntil(LocalDateTime.parse("2022-12-05 20:00:00", formatter).toInstant(ZoneOffset.UTC)));
        List<Pair<Instant, Instant>> actualValues = SchedulerUtils.parseToObservationSchedules(event, Instant.now(), Instant.now());
        assertArrayEquals(Arrays.stream(expectedValues.toArray()).map(Object::toString).toArray(),
                Arrays.stream(actualValues.toArray()).map(Object::toString).toArray());
    }

    @Test
    @DisplayName("Parsing relative event without recursion")
    void testRelativeEvent() {
        RelativeEvent event = new RelativeEvent()
                .setDtstart(
                    new RelativeDate()
                            .setOffset(new Duration().setValue(1).setUnit(Duration.Unit.DAY))
                            .setTime("10:00")
                ).setDtend(
                    new RelativeDate()
                            .setOffset(new Duration().setValue(1).setUnit(Duration.Unit.DAY))
                            .setTime("11:30")
                );

        Instant start = Instant.ofEpochSecond(1700118000); // Thursday, 30. November 2023 00:00:00
        Instant maxEnd = Instant.ofEpochSecond(1701302400); // Thursday, 16. November 2023 07:00:00

        List<Pair<Instant, Instant>> events =  SchedulerUtils.parseToObservationSchedulesForRelativeEvent(event, start, maxEnd);
        Assertions.assertEquals(1, events.size());
    }

    @Test
    @DisplayName("Parsing relative event with recursion")
    void testRelativeEventWithRecursion() {
        RelativeEvent event = new RelativeEvent()
                .setDtstart(
                        new RelativeDate()
                                .setOffset(new Duration().setValue(1).setUnit(Duration.Unit.DAY))
                                .setTime("10:00")
                ).setDtend(
                        new RelativeDate()
                                .setOffset(new Duration().setValue(1).setUnit(Duration.Unit.DAY))
                                .setTime("11:30")
                ).setRrrule(
                        new RelativeRecurrenceRule()
                                .setEndAfter(new Duration().setValue(10).setUnit(Duration.Unit.DAY))
                                .setFrequency(new Duration().setValue(2).setUnit(Duration.Unit.DAY))
                );

        Instant start = Instant.ofEpochSecond(1700118000); // Thursday, 16. November 2023 07:00:00
        Instant maxEnd = Instant.ofEpochSecond(1701302400); // Thursday, 30. November 2023 00:00:00

        List<Pair<Instant, Instant>> events =  SchedulerUtils.parseToObservationSchedulesForRelativeEvent(event, start, maxEnd);
        Assertions.assertEquals(5, events.size());
    }

    @Test
    @DisplayName("Parsing relative event with recursion (long run)")
    void testRelativeEventWithRecursionLongRun() {
        RelativeEvent event = new RelativeEvent()
                .setDtstart(
                        new RelativeDate()
                                .setOffset(new Duration().setValue(1).setUnit(Duration.Unit.DAY))
                                .setTime("10:00")
                ).setDtend(
                        new RelativeDate()
                                .setOffset(new Duration().setValue(1).setUnit(Duration.Unit.DAY))
                                .setTime("11:30")
                ).setRrrule(
                        new RelativeRecurrenceRule()
                                .setEndAfter(new Duration().setValue(100).setUnit(Duration.Unit.DAY))
                                .setFrequency(new Duration().setValue(3).setUnit(Duration.Unit.DAY))
                );

        Instant start = Instant.ofEpochSecond(1700118000); // Thursday, 16. November 2023 07:00:00
        Instant maxEnd = Instant.ofEpochSecond(1701302400); // Thursday, 30. November 2023 00:00:00

        List<Pair<Instant, Instant>> events =  SchedulerUtils.parseToObservationSchedulesForRelativeEvent(event, start, maxEnd);
        Assertions.assertEquals(5, events.size());
    }

}
