/*
 * Copyright (c) 2022 Redlink GmbH.
 */
package io.redlink.more.data.repository;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.OptionalInt;

public final class DbUtils {

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


}
