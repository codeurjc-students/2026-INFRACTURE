package es.codeurjc.infracture;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.testcontainers.postgresql.PostgreSQLContainer;

import es.codeurjc.infracture.catalog.domain.ComponentTemplate;
import es.codeurjc.infracture.catalog.domain.ComponentType;
import es.codeurjc.infracture.catalog.persistence.ComponentTemplateRepository;

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

        @Bean
        @Profile("integration-test")
        ApplicationRunner catalogueVariants(ComponentTemplateRepository repository) {
            return args -> {
                for (int variant = 1; variant <= 6; variant++) {
                    repository.save(new ComponentTemplate(
                            "worker-variant-" + variant,
                            "Worker variant " + variant,
                            ComponentType.WORKER,
                            true));
                }
            };
        }
    }
}
