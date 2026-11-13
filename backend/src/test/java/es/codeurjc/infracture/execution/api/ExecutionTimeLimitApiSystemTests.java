package es.codeurjc.infracture.execution.api;

import static es.codeurjc.infracture.execution.api.ExecutionApiSystemTests.ANOTHER_RUNNING;
import static es.codeurjc.infracture.execution.api.ExecutionApiSystemTests.ORDERS;
import static es.codeurjc.infracture.execution.api.ExecutionApiSystemTests.run;
import static es.codeurjc.infracture.execution.api.ExecutionApiSystemTests.statelessScenario;
import static es.codeurjc.infracture.shared.api.ProblemResponses.problem;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
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
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

// The limit is cut to one second, so the test reaches it without waiting 30 real minutes.
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(locations = "classpath:admin-test.properties",
        properties = {"infracture.execution.reconciler.enabled=false",
            "infracture.execution.cleanup-retry-pause=0s",
            "infracture.execution.max-duration=1s"})
@Testcontainers
class ExecutionTimeLimitApiSystemTests {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.7-alpine");

    @LocalServerPort
    private int port;

    @Autowired
    private ExecutionReconciler reconciler;

    @MockitoBean
    private RuntimeEngine engine;

    @MockitoBean
    private ServiceProbe probe;

    @Test
    void endsTheExperimentAtTheLimitAndKeepsRunBlockedUntilTheCleanupSucceeds() throws InterruptedException {
        when(engine.status(anyMap())).thenReturn(Map.of("c-" + ORDERS, ComponentStatus.HEALTHY));
        ApiSession alice = ApiSession.signUp(port);
        ApiSession bob = ApiSession.signUp(port);
        String scenario = statelessScenario(alice);
        String other = statelessScenario(bob);
        String current = scenario + "/executions/current";
        alice.write().body(run(2)).post(scenario + "/executions").then().statusCode(202)
            .body("deadlineAt", nullValue()).body("secondsLeft", nullValue());

        reconciler.reconcile();
        alice.read().get(current).then().body("phase", equalTo("RUNNING"))
            .body("deadlineAt", notNullValue()).body("secondsLeft", lessThanOrEqualTo(1));
        doThrow(new RuntimeEngineException("docker network rm failed: network has active endpoints"))
            .doNothing()
            .when(engine).down(anyMap(), anyMap());
        Thread.sleep(Duration.ofMillis(1100));
        reconciler.reconcile();

        alice.read().get(current).then()
            .body("phase", equalTo("STOPPING")).body("stopCause", equalTo("TIME_LIMIT"))
            .body("secondsLeft", nullValue()).body("cleanupFailures", equalTo(1))
            .body("releasedAt", nullValue());
        bob.write().body(run(2)).post(other + "/executions").then().spec(problem(409, ANOTHER_RUNNING));
        reconciler.reconcile();
        alice.read().get(current).then()
            .body("phase", equalTo("RELEASED")).body("outcome", equalTo("COMPLETED"))
            .body("stopCause", equalTo("TIME_LIMIT")).body("failureReason", nullValue());
        bob.write().body(run(2)).post(other + "/executions").then().statusCode(202);
    }
}
