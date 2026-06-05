package com.novel2script.infrastructure.config;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Flyway database migration configuration.
 * Spring Boot auto-configures Flyway; this class provides
 * programmatic migration access for testing and manual triggers.
 */
@Configuration
public class FlywayConfig {

    @Autowired
    private DataSource dataSource;

    /**
     * Programmatic Flyway instance for manual migration triggers.
     */
    public Flyway flyway() {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .table("flyway_schema_history")
                .encoding("UTF-8")
                .baselineOnMigrate(true)
                .load();
    }

    public void migrate() {
        flyway().migrate();
    }

    public void repair() {
        flyway().repair();
    }
}
