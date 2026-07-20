/*
 * Copyright (c) 2022 Redlink GmbH.
 */
package io.redlink.more.data.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.redlink.more.data.model.scheduler.Duration;
import io.redlink.more.data.model.scheduler.ScheduleEvent;

import java.sql.*;
import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

public final class DbUtils {

    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());
    public static final Calendar tzUTC = Calendar.getInstance(TimeZone.getTimeZone(ZoneOffset.UTC));

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

    public static Duration readDuration(ResultSet row, String columnLabel) throws SQLException {
        var rawValue = row.getString(columnLabel);
        if(rawValue == null) return null;
        try {
            return MAPPER.readValue(rawValue, Duration.class);
        } catch (JsonProcessingException e) {
            throw new SQLDataException("Could not read Duration from column '" + columnLabel + "'", e);
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

    /**
     * Consumes Array elements from a column of the result set
     * @param rs the result set
     * @param columnLabel the name of the column in the parsed result set
     * @param type the expected type. Elements that are not of that type are filtered
     * @param collector the collector for the array elements
     * @return <code>true</code> if the column was present. <code>false</code> if <code>null</code>
     * @param <T>
     * @throws SQLException
     */
    public static <T> boolean consumeArray(ResultSet rs, String columnLabel, Class<T> type, Consumer<T> collector) throws SQLException {
        Array sqlArray = rs.getArray(columnLabel);
        if (!rs.wasNull()) {
            Stream.of((Object[]) sqlArray.getArray())
                    .filter(Objects::nonNull) //instead of an empty Array SQL adds a NULL element at idx:0 ...
                    .filter(e -> type.isAssignableFrom(e.getClass()))
                    .map(type::cast)
                    .forEach(collector);
            return true;
        } else { //no need to process a NULL value
            return false;
        }
    }

    public static <T> Set<T> readSet(ResultSet rs, String columnLabel, Class<T> type) throws SQLException {
        Set<T> set = new HashSet<>();
        consumeArray(rs, columnLabel, type, set::add);
        return set;
    }

    public static <T> List<T> readList(ResultSet rs, String columnLabel, Class<T> type) throws SQLException {
        List<T> list = new ArrayList<>();
        consumeArray(rs, columnLabel, type, list::add);
        return list;
    }
}
