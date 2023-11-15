/*
 * Copyright (c) 2022 Redlink GmbH.
 */
package io.redlink.more.data.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.redlink.more.data.model.scheduler.ScheduleEvent;

import java.sql.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.OptionalInt;

public final class DbUtils {

    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private DbUtils() {}

    public static LocalDate toLocalDate(String date) {
        if (date != null) {
            return LocalDate.parse(date);
        }
        return null;
    }

    public static LocalDate toLocalDate(Date date) {
        if (date != null) {
            return date.toLocalDate();
        }
        return null;
    }

    public static Instant toInstant(Timestamp timestamp) {
        if (timestamp != null) {
            return timestamp.toInstant();
        }
        return null;
    }

    public static OptionalInt readOptionalInt(ResultSet row, String columnLabel) throws SQLException {
        final int value = row.getInt(columnLabel);
        if (row.wasNull()) {
            return OptionalInt.empty();
        } else {
            return OptionalInt.of(value);
        }
    }

    public static ScheduleEvent readEvent(ResultSet row, String columnLabel) throws SQLException {
        var rawValue = row.getString(columnLabel);
        if(rawValue == null) return null;
        try {
            return MAPPER.readValue(rawValue, ScheduleEvent.class);
        } catch (JsonProcessingException e) {
            throw new SQLDataException("Could not read Event from column '" + columnLabel + "'", e);
        }
    }

    public static Object readObject(ResultSet row, String columnLabel) throws SQLException {
        var rawValue = row.getString(columnLabel);
        if(rawValue == null) return null;
        try {
            return MAPPER.readValue(rawValue, Object.class);
        } catch (JsonProcessingException e) {
            throw new SQLDataException("Could not read Object from column '" + columnLabel + "'", e);
        }
    }

    public static Object mergeObjects(Object o1, Object o2) {
        try {
            JsonNode n1 = MAPPER.valueToTree(o1);
            JsonNode n2 = MAPPER.valueToTree(o2);
            return MAPPER.treeToValue(MAPPER.updateValue(n1,n2), Object.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Could not merge json Objects", e);
        }
    }

}
