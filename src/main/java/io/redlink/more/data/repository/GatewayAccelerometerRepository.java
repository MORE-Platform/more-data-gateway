package io.redlink.more.data.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import io.redlink.more.data.model.DataPoint;

@Service
public class GatewayAccelerometerRepository {

    private final JdbcTemplate jdbcTemplate;

    public GatewayAccelerometerRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int[] BatchInsertAccelerometers(List<DataPoint> dataBulk) {
        return this.jdbcTemplate.batchUpdate(
			"INSERT INTO public.accelerometer (ts,x) VALUES (?,?)",            
			new BatchPreparedStatementSetter() {                           
				public void setValues(PreparedStatement ps, int i) throws SQLException {                    
					ps.setTimestamp(1, Timestamp.from(dataBulk.get(i).effectiveDateTime()));
					ps.setDouble(2, Double.parseDouble(dataBulk.get(i).data().get("acc_float").toString()));
				}

				public int getBatchSize() {
					return dataBulk.size();
				}

			});
    }
}
