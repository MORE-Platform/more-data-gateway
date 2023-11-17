/*
 * Copyright LBI-DHP and/or licensed to LBI-DHP under one or more
 * contributor license agreements (LBI-DHP: Ludwig Boltzmann Institute
 * for Digital Health and Prevention -- A research institute of the
 * Ludwig Boltzmann Gesellschaft, Oesterreichische Vereinigung zur
 * Foerderung der wissenschaftlichen Forschung).
 * Licensed under the Elastic License 2.0.
 */
package io.redlink.more.data.repository;

import org.junit.jupiter.api.Test;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DbUtilsTest {

    @Test
    void mergeObjectsTest() throws SQLException {
        ResultSet set1 = mock(ResultSet.class);
        ResultSet set2 = mock(ResultSet.class);
        ResultSet set3 = mock(ResultSet.class);
        when(set1.getString(any())).thenReturn("{\"hello\":\"world\",\"over\":\"write\"}");
        when(set2.getString(any())).thenReturn("{\"other\":\"value\",\"over\":\"written\"}");
        when(set3.getString(any())).thenReturn(null);

        Object o1 = DbUtils.readObject(set1, "");
        Object o2 = DbUtils.readObject(set2, "");
        Object o3 = DbUtils.readObject(set3, "");

        Object res = DbUtils.mergeObjects(o1, o2);
        assertTrue(res instanceof Map);
        assertEquals(3, ((Map) res).size());

        Object res2 = DbUtils.mergeObjects(o1,o3);
        Object res3 = DbUtils.mergeObjects(o3,o1);
        Object res4 = DbUtils.mergeObjects(o3,o3);

        assertEquals(2, ((Map) res2).size());
        assertEquals(2, ((Map) res3).size());
        assertNull(res4);
    }
}
