package es.codeurjc.infracture;

import java.util.Map;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;

import es.codeurjc.infracture.catalog.domain.ComponentConfiguration;
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
            return new PostgreSQLContainer("postgres:18.7-alpine");
        }

        // Disposable image storage, so the harness never touches the developer's avatars.
        @Bean
        GenericContainer<?> storageContainer() {
            return new GenericContainer<>("rustfs/rustfs:1.0.6")
                    .withEnv("RUSTFS_ACCESS_KEY", "harness-storage")
                    .withEnv("RUSTFS_SECRET_KEY", "harness-storage-secret")
                    .withExposedPorts(9000)
                    .waitingFor(Wait.forHttp("/health").forPort(9000));
        }

        @Bean
        DynamicPropertyRegistrar storageProperties(GenericContainer<?> storageContainer) {
            return registry -> {
                registry.add("infracture.storage.endpoint", () -> "http://%s:%d"
                        .formatted(storageContainer.getHost(), storageContainer.getMappedPort(9000)));
                registry.add("infracture.storage.access-key", () -> "harness-storage");
                registry.add("infracture.storage.secret-key", () -> "harness-storage-secret");
            };
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
                            true, new ComponentConfiguration("CONSUMER", Map.of())));
                }
            };
        }
    }
}
