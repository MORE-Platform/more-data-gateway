package io.redlink.more.data.schedule;

import biweekly.component.VEvent;
import biweekly.util.DayOfWeek;
import biweekly.util.Frequency;
import biweekly.util.Recurrence;
import biweekly.util.com.google.ical.compat.javautil.DateIterator;
import io.redlink.more.data.model.scheduler.*;
import org.apache.commons.lang3.tuple.Pair;

import java.sql.Date;
import java.time.*;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TimeZone;

public class SchedulerUtils {

    public static Instant getEnd(RelativeEvent event, Instant start, Instant end) {
        return parseToObservationSchedulesForRelativeEvent(event, start, end)
                .stream().map(Pair::getRight).max(Instant::compareTo).orElse(null);
    }

    public static List<Pair<Instant, Instant>> parseToObservationSchedulesForRelativeEvent(
            RelativeEvent event, Instant start, Instant maxEnd) {

        List<Pair<Instant, Instant>> events = new ArrayList<>();

        start = shiftStartIfNecessary(start);

        Pair<Instant, Instant> currentEvt = Pair.of(toInstant(event.getDtstart(), start), toInstant(event.getDtend(), start));

        if(event.getRrrule() != null) {
            RelativeRecurrenceRule rrule = event.getRrrule();
            Instant maxEndOfRule = currentEvt.getRight().plus(rrule.getEndAfter().getValue(), rrule.getEndAfter().getUnit().toTemporalUnit());
            maxEnd = maxEnd.isBefore(maxEndOfRule) ? maxEnd : maxEndOfRule;
            long durationInMs = currentEvt.getRight().toEpochMilli() - currentEvt.getLeft().toEpochMilli();

            while(currentEvt.getRight().isBefore(maxEnd)) {
                events.add(currentEvt);
                Instant estart = currentEvt.getLeft().plus(rrule.getFrequency().getValue(), rrule.getFrequency().getUnit().toTemporalUnit());
                currentEvt = Pair.of(estart, estart.plusMillis(durationInMs));
            }
        } else {
            events.add(currentEvt);
        }

        return events;
    }

    private static Instant shiftStartIfNecessary(Instant start) {
        // TODO
        return start;
    }

    private static Instant toInstant(RelativeDate date, Instant start) {
        return ZonedDateTime.ofInstant(start.plus(date.getOffset().getValue() - 1L, date.getOffset().getUnit().toTemporalUnit()), ZoneId.systemDefault())
                .withHour(date.getHours())
                .withMinute(date.getMinutes()).toInstant();
    }

    public static List<Pair<Instant, Instant>> parseToObservationSchedulesForEvent(Event event) {
        List<Pair<Instant, Instant>> observationSchedules = new ArrayList<>();
        if(event.getDateStart() != null && event.getDateEnd() != null) {
            VEvent iCalEvent = parseToICalEvent(event);
            long eventDuration = getEventTime(event);
            DateIterator it = iCalEvent.getDateIterator(TimeZone.getDefault());
            while (it.hasNext()) {
                Instant ostart = it.next().toInstant();
                Instant oend = ostart.plus(eventDuration, ChronoUnit.SECONDS);
                observationSchedules.add(Pair.of(ostart, oend));
            }
        }
        // TODO edge cases if calculated days are not consecutive (e.g. first weekend -> first of month is a sunday)
        return observationSchedules;
    }

    public static List<Pair<Instant, Instant>> parseToObservationSchedules(ScheduleEvent scheduleEvent, Instant start, Instant end) {
        if(Event.class.isAssignableFrom(scheduleEvent.getClass())) {
            return parseToObservationSchedulesForEvent((Event) scheduleEvent);
        } else {
            return parseToObservationSchedulesForRelativeEvent((RelativeEvent) scheduleEvent, start, end);
        }
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
