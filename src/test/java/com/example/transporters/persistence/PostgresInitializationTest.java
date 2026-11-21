package com.example.transporters.persistence;

import com.example.transporters.api.InputData;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = "jdbc:postgresql:.*")
class PostgresInitializationTest {
    @Test
    void concurrentInstancesInitializeOneTableAndShareTheSavedDataset() throws Exception {
        String jdbcUrl = System.getenv("TEST_DATABASE_URL");
        String username = System.getenv("PGUSER");
        String password = System.getenv("PGPASSWORD");
        String schema = "transporter_test_" + UUID.randomUUID().toString().replace("-", "");
        JdbcTemplate admin = new JdbcTemplate(new DriverManagerDataSource(jdbcUrl, username, password));
        String schemaUrl = jdbcUrl + (jdbcUrl.contains("?") ? "&" : "?") + "currentSchema=" + schema;
        ExecutorService executor = Executors.newFixedThreadPool(4);

        admin.execute("CREATE SCHEMA " + schema);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> initializations = new ArrayList<>();
            for (int index = 0; index < 4; index++) {
                initializations.add(executor.submit(() -> {
                    start.await();
                    DriverManagerDataSource dataSource = new DriverManagerDataSource(schemaUrl, username, password);
                    new ResourceDatabasePopulator(new ClassPathResource("schema-postgres.sql"))
                            .execute(dataSource);
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> initialization : initializations) {
                initialization.get(20, TimeUnit.SECONDS);
            }

            JdbcTemplate firstInstance = new JdbcTemplate(new DriverManagerDataSource(schemaUrl, username, password));
            JdbcTemplate secondInstance = new JdbcTemplate(new DriverManagerDataSource(schemaUrl, username, password));
            InputRepository writer = new InputRepository(firstInstance, new ObjectMapper(), schemaUrl);
            InputRepository reader = new InputRepository(secondInstance, new ObjectMapper(), schemaUrl);
            InputData input = new InputData(
                    Collections.singletonList(new InputData.Lane(17L, "Mumbai", "Delhi")),
                    Collections.singletonList(new InputData.Transporter(23L, "Shared transporter",
                            Collections.singletonList(new InputData.LaneQuote(17L, new BigDecimal("12.34"))))));

            writer.replace(input);
            assertThat(reader.findCurrent()).usingRecursiveComparison().isEqualTo(input);
            reader.replace(input);
            assertThat(writer.findCurrent()).usingRecursiveComparison().isEqualTo(input);
            assertThat(firstInstance.queryForObject("SELECT COUNT(*) FROM input_dataset", Integer.class))
                    .isEqualTo(1);
            assertThat(admin.queryForObject("SELECT COUNT(*) FROM information_schema.tables "
                    + "WHERE table_schema = ? AND table_name = 'input_dataset'", Integer.class, schema))
                    .isEqualTo(1);
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(20, TimeUnit.SECONDS);
            // Only the randomly named schema created by this test is removed.
            admin.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }
}
