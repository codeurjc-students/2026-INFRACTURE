package es.codeurjc.infracture;

import es.codeurjc.infracture.catalog.domain.ComponentConfiguration;
import es.codeurjc.infracture.catalog.domain.ComponentTemplate;
import es.codeurjc.infracture.catalog.domain.ComponentType;
import es.codeurjc.infracture.catalog.persistence.ComponentTemplateRepository;
import es.codeurjc.infracture.execution.application.LoadReport;
import es.codeurjc.infracture.execution.application.LogLine;
import es.codeurjc.infracture.execution.application.ResourceUsage;
import es.codeurjc.infracture.execution.application.RuntimeEngine;
import es.codeurjc.infracture.execution.application.RuntimeEngineException;
import es.codeurjc.infracture.execution.application.RuntimeStatus;
import es.codeurjc.infracture.execution.application.StartCancelledException;
import es.codeurjc.infracture.execution.domain.ExecutionPlan;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
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

        // Real Docker runs are covered by ComposeRuntimeEngineIntegrationTests and test-execution-stack.sh;
        // this layer verifies the HTTP client contract without racing missing demo images.
        @Bean
        @Primary
        @Profile("integration-test")
        RuntimeEngine integrationTestRuntimeEngine() {
            return new WaitingRuntimeEngine();
        }
    }

    private static final class WaitingRuntimeEngine implements RuntimeEngine {

        @Override
        public void up(ExecutionPlan plan, BooleanSupplier cancelled) {
            waitForStop(cancelled);
        }

        @Override
        public void startLoad(ExecutionPlan plan, BooleanSupplier cancelled) {
            waitForStop(cancelled);
        }

        @Override
        public LoadReport stopLoad(ExecutionPlan plan) {
            return new LoadReport(null, null);
        }

        @Override
        public RuntimeStatus status(ExecutionPlan plan) {
            return new RuntimeStatus(Map.of(), null);
        }

        @Override
        public List<LogLine> logs(ExecutionPlan plan, Instant since) {
            return List.of();
        }

        @Override
        public Map<UUID, ResourceUsage> usage(ExecutionPlan plan) {
            return Map.of();
        }

        @Override
        public void down(ExecutionPlan plan) {
            // Nothing was started in Docker.
        }

        private static void waitForStop(BooleanSupplier cancelled) {
            while (!cancelled.getAsBoolean()) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeEngineException("Start interrupted", exception);
                }
            }
            throw new StartCancelledException("Start cancelled");
        }
    }
}
