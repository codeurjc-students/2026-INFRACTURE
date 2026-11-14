package es.codeurjc.infracture.execution.api;

import static es.codeurjc.infracture.execution.api.ExecutionApiSystemTests.ANOTHER_RUNNING;
import static es.codeurjc.infracture.execution.api.ExecutionApiSystemTests.ORDERS;
import static es.codeurjc.infracture.execution.api.ExecutionApiSystemTests.run;
import static es.codeurjc.infracture.execution.api.ExecutionApiSystemTests.statelessScenario;
import static es.codeurjc.infracture.shared.api.ProblemResponses.problem;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import es.codeurjc.infracture.execution.application.ComponentStatus;
import es.codeurjc.infracture.execution.application.ExecutionReconciler;
import es.codeurjc.infracture.execution.application.RuntimeEngine;
import es.codeurjc.infracture.execution.application.RuntimeEngineException;
import es.codeurjc.infracture.execution.application.ServiceProbe;
import es.codeurjc.infracture.project.api.ApiSession;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

// A second reconciler built after the experiment started plays the backend process that comes back
// after a restart: it finds the execution that the first one left open.
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(locations = "classpath:admin-test.properties",
        properties = {"infracture.execution.reconciler.enabled=false",
            "infracture.execution.cleanup-retry-pause=0s"})
@Testcontainers
class ExecutionRecoveryApiSystemTests {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.7-alpine");

    @LocalServerPort
    private int port;

    @Autowired
    private ExecutionReconciler reconciler;

    @Autowired
    private AutowireCapableBeanFactory beans;

    @MockitoBean
    private RuntimeEngine engine;

    @MockitoBean
    private ServiceProbe probe;

    @Test
    void interruptsTheExperimentAfterARestartAndKeepsRunBlockedUntilItsContainersAreGone() {
        when(engine.status(anyMap())).thenReturn(Map.of("c-" + ORDERS, ComponentStatus.HEALTHY));
        ApiSession alice = ApiSession.signUp(port);
        ApiSession bob = ApiSession.signUp(port);
        String scenario = statelessScenario(alice);
        String other = statelessScenario(bob);
        String current = scenario + "/executions/current";
        alice.write().body(run(2)).post(scenario + "/executions").then().statusCode(202)
            .body("interruptedAt", nullValue()).body("interruptedPhase", nullValue());
        reconciler.reconcile();
        doThrow(new RuntimeEngineException("docker compose down failed: Cannot connect to the Docker daemon"))
            .doNothing()
            .when(engine).down(anyMap(), anyMap());

        ExecutionReconciler restarted = beans.createBean(ExecutionReconciler.class);
        restarted.reconcile();

        alice.read().get(current).then()
            .body("phase", equalTo("STOPPING")).body("stopCause", equalTo("INTERRUPTION"))
            .body("interruptedPhase", equalTo("RUNNING")).body("interruptedAt", notNullValue())
            .body("secondsLeft", nullValue()).body("cleanupFailures", equalTo(1))
            .body("cleanupError", equalTo("docker compose down failed: Cannot connect to the Docker daemon"));
        bob.write().body(run(2)).post(other + "/executions").then().spec(problem(409, ANOTHER_RUNNING));
        restarted.reconcile();
        alice.read().get(current).then()
            .body("phase", equalTo("RELEASED")).body("outcome", equalTo("INTERRUPTED"))
            .body("interruptedPhase", equalTo("RUNNING"));
        bob.write().body(run(2)).post(other + "/executions").then().statusCode(202);
    }
}
