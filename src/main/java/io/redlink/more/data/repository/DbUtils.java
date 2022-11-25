/*
 * Copyright (c) 2022 Redlink GmbH.
 */
package io.redlink.more.data.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.redlink.more.data.model.Event;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
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

    public static Event readValue(Object o) {
        if(o == null) return null;
        try {
            return MAPPER.readValue(o.toString(), Event.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }


}
