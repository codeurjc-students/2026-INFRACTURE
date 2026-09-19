package es.codeurjc.infracture;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

public class TestInfractureBackendApplication {

    public static void main(String[] args) {
        SpringApplication.from(InfractureBackendApplication::main)
                .with(PostgresTestcontainersConfiguration.class)
                .run(args);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class PostgresTestcontainersConfiguration {

        @Bean
        @ServiceConnection
        PostgreSQLContainer postgresContainer() {
            return new PostgreSQLContainer("postgres:18.6-alpine");
        }
    }
}
