package com.kopite.devspace;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class DevspaceApplicationTests {

    private final JdbcTemplate jdbcTemplate;

    private final Environment environment;

    @Autowired
    DevspaceApplicationTests(JdbcTemplate jdbcTemplate, Environment environment) {
        this.jdbcTemplate = jdbcTemplate;
        this.environment = environment;
    }

    @Test
    void contextLoads() {
        assertEquals("validate", environment.getProperty("spring.jpa.hibernate.ddl-auto"));
    }

    @Test
    void connectsToPostgres() {
        assertEquals(1, jdbcTemplate.queryForObject("SELECT 1", Integer.class));
    }

}
