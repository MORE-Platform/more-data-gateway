package io.redlink.more.data.controller;

import io.redlink.more.data.api.app.v1.webservices.CalendarApi;
import io.redlink.more.data.service.CalendarService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Controller
@RestController
@RequestMapping(value = "/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
public class CalendarApiV1Controller implements CalendarApi {

    private final CalendarService calendarService;

    public CalendarApiV1Controller(CalendarService calendarService) {
        this.calendarService = calendarService;
    }

    @Override
    public ResponseEntity<String> getStudyCalendar(Long studyId) {
        return ResponseEntity.of(
                this.calendarService.getICalendarString(studyId)
        );
    }
}
