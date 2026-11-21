package com.example.transporters.persistence;

import com.example.transporters.api.InputData;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class InputRepositoryTest {
    @TempDir
    Path dataDirectory;

    @Test
    void savedDatasetSurvivesDatabaseShutdownAndReopening() {
        String jdbcUrl = "jdbc:h2:file:" + dataDirectory.resolve("transporters") + ";DB_CLOSE_ON_EXIT=FALSE";
        InputData input = new InputData(
                Collections.singletonList(new InputData.Lane(17L, "Mumbai", "Delhi")),
                Collections.singletonList(new InputData.Transporter(23L, "Persisted transporter",
                        Collections.singletonList(new InputData.LaneQuote(17L, new BigDecimal("12.34"))))));

        SingleConnectionDataSource originalDataSource = new SingleConnectionDataSource(jdbcUrl, "sa", "", true);
        try {
            new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(originalDataSource);
            JdbcTemplate originalJdbc = new JdbcTemplate(originalDataSource);
            InputRepository originalRepository = new InputRepository(originalJdbc, new ObjectMapper(), jdbcUrl);
            originalRepository.replace(input);
        } finally {
            originalDataSource.destroy();
        }

        SingleConnectionDataSource reopenedDataSource = new SingleConnectionDataSource(jdbcUrl, "sa", "", true);
        try {
            new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(reopenedDataSource);
            JdbcTemplate reopenedJdbc = new JdbcTemplate(reopenedDataSource);
            InputRepository reopenedRepository = new InputRepository(reopenedJdbc, new ObjectMapper(), jdbcUrl);
            assertThat(reopenedRepository.findCurrent()).usingRecursiveComparison().isEqualTo(input);
            assertThat(reopenedJdbc.queryForObject("SELECT COUNT(*) FROM input_dataset", Integer.class))
                    .isEqualTo(1);
            assertThat(dataDirectory.resolve("transporters.mv.db")).exists();
        } finally {
            reopenedDataSource.destroy();
        }
    }
}
