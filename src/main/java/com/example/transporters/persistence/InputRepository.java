package com.example.transporters.persistence;

import com.example.transporters.api.ApiException;
import com.example.transporters.api.InputData;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.beans.factory.annotation.Value;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

@Repository
public class InputRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final String upsertSql;

    public InputRepository(JdbcTemplate jdbc, ObjectMapper mapper,
                           @Value("${spring.datasource.url}") String jdbcUrl) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.upsertSql = jdbcUrl.startsWith("jdbc:postgresql:")
                ? "INSERT INTO input_dataset (id, payload) VALUES (1, ?) "
                    + "ON CONFLICT (id) DO UPDATE SET payload = EXCLUDED.payload"
                : "MERGE INTO input_dataset (id, payload) KEY (id) VALUES (1, ?)";
    }

    public void replace(InputData input) {
        // One database statement atomically publishes a complete, validated dataset.
        try {
            jdbc.update(upsertSql, mapper.writeValueAsString(input));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize the input dataset", exception);
        }
    }

    public InputData findCurrent() {
        List<String> rows = jdbc.query("SELECT payload FROM input_dataset WHERE id = 1",
                (result, row) -> result.getString("payload"));
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NO_INPUT_DATA",
                    "Submit lanes and transporter quotes to /api/v1/transporters/input first.");
        }
        try {
            return mapper.readValue(rows.get(0), InputData.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not read the saved input dataset", exception);
        }
    }
}
