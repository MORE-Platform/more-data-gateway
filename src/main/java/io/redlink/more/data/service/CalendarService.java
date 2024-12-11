package io.redlink.more.data.service;

import biweekly.Biweekly;
import biweekly.ICalendar;
import biweekly.component.VEvent;
import io.redlink.more.data.model.Observation;
import io.redlink.more.data.model.RoutingInfo;
import io.redlink.more.data.model.StudyDurationInfo;
import io.redlink.more.data.model.scheduler.Event;
import io.redlink.more.data.repository.StudyRepository;
import io.redlink.more.data.schedule.SchedulerUtils;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.TimeZone;

import static io.redlink.more.data.schedule.SchedulerUtils.shiftStartIfObservationAlreadyEnded;

@Service
public class CalendarService {
    private final StudyRepository studyRepository;

    public CalendarService(StudyRepository studyRepository) {
        this.studyRepository = studyRepository;
    }

    public Optional<String> getICalendarString(Long studyId) {

        return studyRepository.findStudy(new RoutingInfo(studyId, -1, -1, false, false), false).map(study -> {
            ICalendar ical = new ICalendar();

            VEvent iCalEvent = new VEvent();
            iCalEvent.addCategories("General");
            iCalEvent.setSummary("Study: " + study.title());
            iCalEvent.setDateStart(Date.from(study.plannedStartDate().atStartOfDay(TimeZone.getDefault().toZoneId()).toInstant()), false);
            iCalEvent.setDateEnd(Date.from(study.endDate().atTime(23, 59).atZone(TimeZone.getDefault().toZoneId()).toInstant()), true);
            ical.addEvent(iCalEvent);

            final Instant start = shiftStartIfObservationAlreadyEnded(
                    study.plannedStartDate().atStartOfDay(ZoneId.systemDefault()).toInstant(),
                    study.observations()
            );

            StudyDurationInfo info = studyRepository.getStudyDurationInfo(studyId)
                    .orElseThrow(() -> new RuntimeException("Cannot create calendar"));

            study.observations().forEach(o -> {
                String type = getTpeFor(o);
                SchedulerUtils.parseToObservationSchedules(
                        o.observationSchedule(), start, info.getDurationFor(o.groupId()).getEnd(start)
                ).forEach(p -> {
                    VEvent oEvent = new VEvent();
                    oEvent.addCategories("Observation", o.groupId() != null ? ("Group" + o.groupId()) : "NoGroup");
                    oEvent.setSummary(getSummaryFor(o, type));
                    oEvent.setDateStart(Date.from(p.getMinimum()));
                    oEvent.setDateEnd(Date.from(p.getMaximum()));
                    ical.addEvent(oEvent);
                });
            });
            return Biweekly.write(ical).go();
        });
    }

    private String getTpeFor(Observation o) {
        if(o.observationSchedule() != null && Event.class.isAssignableFrom(o.observationSchedule().getClass())) {
            return "ABS";
        }
        return "REL";
    }

    private String getSummaryFor(Observation o, String t) {
        return "(O" + o.observationId()+"G"+o.groupId()+"|"+t+") " + o.title();
    }
}
