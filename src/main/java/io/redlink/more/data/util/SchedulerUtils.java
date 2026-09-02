/*
 * Copyright LBI-DHP and/or licensed to LBI-DHP under one or more
 * contributor license agreements (LBI-DHP: Ludwig Boltzmann Institute
 * for Digital Health and Prevention -- A research institute of the
 * Ludwig Boltzmann Gesellschaft, Oesterreichische Vereinigung zur
 * Foerderung der wissenschaftlichen Forschung).
 * Licensed under the Elastic License 2.0.
 */
package io.redlink.more.data.util;

import biweekly.component.VEvent;
import biweekly.util.DayOfWeek;
import biweekly.util.Frequency;
import biweekly.util.Recurrence;
import biweekly.util.com.google.ical.compat.javautil.DateIterator;
import io.redlink.more.data.model.Observation;
import io.redlink.more.data.model.ParticipantObservationSeed;
import io.redlink.more.data.model.scheduler.Event;
import io.redlink.more.data.model.scheduler.RecurrenceRule;
import io.redlink.more.data.model.scheduler.RelativeDate;
import io.redlink.more.data.model.scheduler.RelativeEvent;
import io.redlink.more.data.model.scheduler.RelativeRecurrenceRule;
import io.redlink.more.data.model.scheduler.ScheduleEvent;
import org.apache.commons.lang3.Range;

import java.sql.Date;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.TimeZone;

public class SchedulerUtils {

    public static List<Range<Instant>> parseToObservationSchedulesForRelativeEvent(
            RelativeEvent event, Instant start, boolean isMilestoneAnchor) {

        final List<Range<Instant>> events = new ArrayList<>();

        Range<Instant> currentEvt = Range.of(
                toInstantFrom(event.getDtstart(), start, isMilestoneAnchor),
                toInstantFrom(event.getDtend(), start, isMilestoneAnchor)
        );

        if (event.getRrrule() != null) {
            RelativeRecurrenceRule rrule = event.getRrrule();
            Instant maxEndOfRule = currentEvt.getMaximum().plus(rrule.getEndAfter().getValue(), rrule.getEndAfter().getUnit().toTemporalUnit());
            long durationInMs = currentEvt.getMaximum().toEpochMilli() - currentEvt.getMinimum().toEpochMilli();

            while (currentEvt.getMaximum().isBefore(maxEndOfRule)) {
                events.add(currentEvt);
                Instant eventStart = currentEvt.getMinimum().plus(rrule.getFrequency().getValue(), rrule.getFrequency().getUnit().toTemporalUnit());
                currentEvt = Range.of(eventStart, eventStart.plusMillis(durationInMs));
            }
        } else {
            events.add(currentEvt);
        }

        return List.copyOf(events);
    }

    private static Instant toInstantFrom(RelativeDate date, Instant start, boolean isMilestoneAnchor) {
        int offsetValue = isMilestoneAnchor
                ? date.getOffset().getValue()
                : date.getOffset().getValue() - 1;
        return start.atZone(ZoneId.systemDefault())
                .plus(offsetValue, date.getOffset().getUnit().toTemporalUnit())
                .with(date.getTime())
                .toInstant();
    }

    public static List<Range<Instant>> parseToObservationSchedulesForEvent(Event event, Instant start, Instant end) {
        List<Range<Instant>> observationSchedules = new ArrayList<>();
        if (event.getDateStart() != null && event.getDateEnd() != null) {
            VEvent iCalEvent = parseToICalEvent(event, end);
            long eventDuration = getEventTime(event);
            DateIterator it = iCalEvent.getDateIterator(TimeZone.getDefault());
            while (it.hasNext()) {
                Instant ostart = it.next().toInstant();
                Instant oend = ostart.plus(eventDuration, ChronoUnit.SECONDS);
                if (ostart.isBefore(end) && oend.isAfter(start)) {
                    observationSchedules.add(Range.of(ostart, oend));
                }
            }
        }
        // TODO edge cases if calculated days are not consecutive (e.g. first weekend -> first of month is a sunday)
        return List.copyOf(observationSchedules);
    }

    public static List<Range<Instant>> parseToObservationSchedules(ParticipantObservationSeed participantObservationSeed, ScheduleEvent scheduleEvent, Instant start, Instant end, boolean isMilestoneAnchor) {
        if (scheduleEvent == null) return Collections.emptyList();
        List<Range<Instant>> ranges = Collections.emptyList();
        if (scheduleEvent instanceof Event event) {
            ranges = parseToObservationSchedulesForEvent(event, start, end);
        } else if (scheduleEvent instanceof RelativeEvent relativeEvent) {
            ranges = parseToObservationSchedulesForRelativeEvent(relativeEvent, start, isMilestoneAnchor);
        }
        return randomSchedule(participantObservationSeed, scheduleEvent, ranges);
    }

    public static Instant shiftStartIfObservationAlreadyEnded(Instant start, List<Observation> observations) {
        // returns start date, if now event ends before, otherwise start date + 1 day
        return observations.stream()
                .filter(o -> o.milestoneId() == null)
                .map(Observation::observationSchedule)
                .filter(scheduleEvent -> scheduleEvent.getType().equals(RelativeEvent.TYPE))
                .map(r -> ((RelativeEvent) r).getDtend())
                .filter(relativeDate -> relativeDate.getOffset().getValue() == 1)
                .map(relativeDate -> start.atZone(ZoneId.systemDefault()).withHour(relativeDate.getHours()).withMinute(relativeDate.getMinutes()).withSecond(0).withNano(0).toInstant())
                .filter(instant -> instant.isBefore(start))
                .map(instant -> start.atZone(ZoneId.systemDefault()).withHour(0).withMinute(0).plusDays(1).toInstant())
                .findFirst()
                .orElse(start);
    }

    private static long getEventTime(Event event) {
        return java.time.Duration.between(event.getDateStart(), event.getDateEnd()).getSeconds();
    }

    private static VEvent parseToICalEvent(Event event, Instant fallBackEnd) {
        VEvent iCalEvent = new VEvent();
        iCalEvent.setDateStart(Date.from(event.getDateStart()));
        iCalEvent.setDateEnd(Date.from(event.getDateEnd()));

        RecurrenceRule eventRecurrence = event.getRRule();
        if (eventRecurrence != null) {
            Recurrence.Builder recurBuilder = new Recurrence.Builder(Frequency.valueOf(eventRecurrence.getFreq()));

            setUntil(recurBuilder, Objects.requireNonNullElse(eventRecurrence.getUntil(), fallBackEnd));
            setCount(recurBuilder, eventRecurrence.getCount());
            setInterval(recurBuilder, eventRecurrence.getInterval());
            setByDay(recurBuilder, eventRecurrence.getByDay(), eventRecurrence.getBySetPos());
            setByHour(recurBuilder, eventRecurrence.getFreq(), event.getDateStart().atZone(TimeZone.getDefault().toZoneId()).getHour());
            setByMinute(recurBuilder, event.getDateStart().atZone(TimeZone.getDefault().toZoneId()).getMinute());
            setByMonth(recurBuilder, eventRecurrence.getByMonth());
            setByMonthDay(recurBuilder, eventRecurrence.getByMonthDay());

            iCalEvent.setRecurrenceRule(new biweekly.property.RecurrenceRule(recurBuilder.build()));
        }
        return iCalEvent;
    }

    private static void setByMinute(Recurrence.Builder builder, Integer minute) {
        if (minute != null) builder.byMinute(minute);
    }

    private static void setByHour(Recurrence.Builder builder, String freq, Integer hour) {
        if (hour != null && !Objects.equals(freq, "HOURLY")) builder.byHour(hour);
    }

    private static void setUntil(Recurrence.Builder builder, Instant until) {
        if (until != null) builder.until(Date.from(until));
    }

    private static void setCount(Recurrence.Builder builder, Integer count) {
        if (count != null) builder.count(count);
    }

    private static void setInterval(Recurrence.Builder builder, Integer interval) {
        if (interval != null) builder.interval(interval);
    }

    private static void setByDay(Recurrence.Builder builder, List<String> byDay, Integer bySetPos) {
        if (byDay != null && bySetPos == null)
            builder.byDay(byDay.stream().map(DayOfWeek::valueOfAbbr).toList());
        if (byDay != null && bySetPos != null)
            byDay.forEach(day -> builder.byDay(bySetPos, DayOfWeek.valueOfAbbr(day)));

    }

    private static void setByMonth(Recurrence.Builder builder, Integer byMonth) {
        if (byMonth != null) builder.byMonth(byMonth);
    }

    private static void setByMonthDay(Recurrence.Builder builder, Integer byMonthDay) {
        if (byMonthDay != null) builder.byMonthDay(byMonthDay);
    }

    public static List<Range<Instant>> randomSchedule(ParticipantObservationSeed participantObservationSeeds, ScheduleEvent event, List<Range<Instant>> ranges) {
        if (participantObservationSeeds == null || participantObservationSeeds.seed() == null || event.getRandomization() == null || !event.getRandomization().state() || event.getRandomization().duration() <= 0) {
            return ranges;
        }
        if (ranges == null || ranges.isEmpty()) {
            return Collections.emptyList();
        }
        Long durationInSeconds = event.getRandomization().duration() * 60L;
        return RandomSchedulerUtils.parseScheduleWithSeed(participantObservationSeeds.seed(), ranges, durationInSeconds);
    }
}
