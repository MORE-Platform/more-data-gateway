/*
 * Copyright LBI-DHP and/or licensed to LBI-DHP under one or more
 * contributor license agreements (LBI-DHP: Ludwig Boltzmann Institute
 * for Digital Health and Prevention -- A research institute of the
 * Ludwig Boltzmann Gesellschaft, Oesterreichische Vereinigung zur
 * Foerderung der wissenschaftlichen Forschung).
 * Licensed under the Elastic License 2.0.
 */
package io.redlink.more.data.schedule;

import biweekly.component.VEvent;
import biweekly.util.DayOfWeek;
import biweekly.util.Frequency;
import biweekly.util.Recurrence;
import biweekly.util.com.google.ical.compat.javautil.DateIterator;
import io.redlink.more.data.model.Event;
import io.redlink.more.data.model.RecurrenceRule;
import org.apache.commons.lang3.tuple.Pair;

import java.sql.Date;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TimeZone;

public class ICalendarParser {

    public static List<Pair<Instant, Instant>> parseToObservationSchedules(Event event) {
        List<Pair<Instant, Instant>> observationSchedules = new ArrayList<>();
        if(event.getDateStart() != null && event.getDateEnd() != null) {
            VEvent iCalEvent = parseToICalEvent(event);
            long eventDuration = getEventTime(event);
            DateIterator it = iCalEvent.getDateIterator(TimeZone.getDefault());
            while (it.hasNext()) {
                Instant start = it.next().toInstant();
                Instant end = start.plus(eventDuration, ChronoUnit.SECONDS);
                observationSchedules.add(Pair.of(start, end));
            }
        }
        // TODO edge cases if calculated days are not consecutive (e.g. first weekend -> first of month is a sunday)
        return observationSchedules;
    }

    private static long getEventTime(Event event) {
        return Duration.between(event.getDateStart(), event.getDateEnd()).getSeconds();
    }

    private static VEvent parseToICalEvent(Event event) {
        VEvent iCalEvent = new VEvent();
        iCalEvent.setDateStart(Date.from(event.getDateStart()));
        iCalEvent.setDateEnd(Date.from(event.getDateEnd()));

        RecurrenceRule eventRecurrence = event.getRRule();
        if (event.getRRule() != null) {
            Recurrence.Builder recurBuilder = new Recurrence.Builder(Frequency.valueOf(eventRecurrence.getFreq()));
            setUntil(recurBuilder, eventRecurrence.getUntil());
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
        if(minute != null) builder.byMinute(minute);
    }

    private static void setByHour(Recurrence.Builder builder, String freq, Integer hour) {
        if(hour != null && !Objects.equals(freq, "HOURLY")) builder.byHour(hour);
    }

    private static void setUntil(Recurrence.Builder builder, Instant until) {
        if(until != null) builder.until(Date.from(until));
    }

    private static void setCount(Recurrence.Builder builder, Integer count) {
        if(count != null) builder.count(count);
    }

    private static void setInterval(Recurrence.Builder builder, Integer interval) {
        if(interval != null) builder.interval(interval);
    }

    private static void setByDay(Recurrence.Builder builder, List<String> byDay, Integer bySetPos) {
        if(byDay != null && bySetPos == null)
            builder.byDay(byDay.stream().map(DayOfWeek::valueOfAbbr).toList());
        if(byDay != null && bySetPos != null)
            byDay.forEach(day -> builder.byDay(bySetPos, DayOfWeek.valueOfAbbr(day)));

    }

    private static void setByMonth(Recurrence.Builder builder, Integer byMonth) {
        if(byMonth != null) builder.byMonth(byMonth);
    }

    private static void setByMonthDay(Recurrence.Builder builder, Integer byMonthDay) {
        if(byMonthDay != null) builder.byMonthDay(byMonthDay);
    }
}
