package es.codeurjc.infracture.execution.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// Starts the reconciler loop. Tests that drive the reconciler by hand turn it off with
// infracture.execution.reconciler.enabled=false, so no tick changes a phase behind their back.
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "infracture.execution.reconciler.enabled", havingValue = "true", matchIfMissing = true)
class ExecutionScheduling {
}
