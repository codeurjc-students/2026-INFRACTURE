package es.codeurjc.infracture.execution.api;

import static es.codeurjc.infracture.execution.api.ExecutionApiSystemTests.ORDERS;
import static es.codeurjc.infracture.execution.api.ExecutionApiSystemTests.ORDERS_HEALTHY;
import static es.codeurjc.infracture.execution.api.ExecutionApiSystemTests.ORDERS_KEY;
import static es.codeurjc.infracture.execution.api.ExecutionApiSystemTests.run;
import static es.codeurjc.infracture.execution.api.ExecutionApiSystemTests.statelessScenario;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import es.codeurjc.infracture.execution.application.CounterScraper;
import es.codeurjc.infracture.execution.application.ExecutionReconciler;
import es.codeurjc.infracture.execution.application.ResourceUsage;
import es.codeurjc.infracture.execution.application.RuntimeEngine;
import es.codeurjc.infracture.execution.application.RuntimeEngineException;
import es.codeurjc.infracture.execution.application.ServiceProbe;
import es.codeurjc.infracture.execution.application.TelemetryCollector;
import es.codeurjc.infracture.execution.persistence.ExecutionRepository;
import es.codeurjc.infracture.project.api.ApiSession;
import es.codeurjc.infracture.project.persistence.ProjectRepository;
import es.codeurjc.infracture.project.persistence.ScenarioRepository;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
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

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(locations = "classpath:admin-test.properties", properties = {
        "infracture.execution.reconciler.enabled=false",
        "infracture.execution.cleanup-retry-pause=0s",
        "infracture.execution.events.poll-interval=1h" })
@Testcontainers
class ExecutionMetricsApiSystemTests {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.7-alpine");

    @LocalServerPort
    private int port;

    @Autowired
    private ExecutionRepository executions;

    @Autowired
    private ScenarioRepository scenarios;

    @Autowired
    private ProjectRepository projects;

    @Autowired
    private ExecutionReconciler reconciler;

    @Autowired
    private TelemetryCollector telemetry;

    @MockitoBean
    private RuntimeEngine engine;

    @MockitoBean
    private ServiceProbe probe;

    @MockitoBean
    private CounterScraper scraper;

    private ApiSession alice;
    private String scenario;
    private long execution;

    @BeforeEach
    void runAnExperiment() {
        executions.deleteAllInBatch();
        scenarios.deleteAllInBatch();
        projects.deleteAllInBatch();
        when(engine.status(any())).thenReturn(ORDERS_HEALTHY);
        when(scraper.scrape(any())).thenReturn(Map.of());
        alice = ApiSession.signUp(port);
        scenario = statelessScenario(alice);
        execution = alice.write().body(run(2)).post(scenario + "/executions").then().statusCode(202)
                .extract().jsonPath().getLong("id");
        reconciler.reconcile();
        reconciler.reconcile();
    }

    @Test
    void showsRealSamplesWithUnitsAndLimitsWhileRunningAndAfterStop() {
        when(engine.usage(any())).thenReturn(Map.of(ORDERS_KEY, new ResourceUsage(25.5, 1_048_576L)));
        when(scraper.scrape(any())).thenReturn(Map.of(ORDERS_KEY, Map.of("requests", 150.0, "errors", 0.0)));
        telemetry.collect();

        alice.read().get(metrics()).then().statusCode(200)
                .body("source", equalTo("DOCKER_STATS"))
                .body("cpuUnit", equalTo("PERCENT_OF_ONE_CORE"))
                .body("memoryUnit", equalTo("BYTES"))
                .body("intervalSeconds", equalTo(5))
                .body("capturing", equalTo(true))
                .body("hasMore", equalTo(false))
                .body("components", hasSize(1))
                .body("components[0].key", equalTo(ORDERS))
                .body("components[0].name", equalTo("orders-api"))
                .body("components[0].cpuLimitPercent", equalTo(50.0f))
                .body("components[0].memoryLimitBytes", equalTo(402_653_184))
                .body("components[0].counters", containsInAnyOrder("requests", "errors"))
                .body("samples", hasSize(1))
                .body("samples[0].id", notNullValue())
                .body("samples[0].componentKey", equalTo(ORDERS))
                .body("samples[0].at", notNullValue())
                .body("samples[0].cpuPercent", equalTo(25.5f))
                .body("samples[0].memoryBytes", equalTo(1_048_576))
                .body("samples[0].counters.requests", equalTo(150.0f))
                .body("samples[0].counters.errors", equalTo(0.0f));

        alice.write().post(scenario + "/executions/" + execution + "/stop").then().statusCode(202);
        alice.read().get(metrics()).then().body("capturing", equalTo(false));
        reconciler.reconcile();

        alice.read().get(metrics()).then().statusCode(200)
                .body("capturing", equalTo(false))
                .body("samples.cpuPercent", contains(25.5f));
    }

    @Test
    void tellsAMissingReadingApartFromARealZero() {
        when(engine.usage(any())).thenReturn(Map.of(ORDERS_KEY, new ResourceUsage(0.0, 0L)));
        when(scraper.scrape(any())).thenReturn(Map.of(ORDERS_KEY, Map.of("requests", 0.0)));
        telemetry.collect();
        when(engine.usage(any())).thenThrow(new RuntimeEngineException("docker stats did not finish"));
        when(scraper.scrape(any())).thenReturn(Map.of());
        telemetry.collect();

        alice.read().get(metrics()).then().statusCode(200)
                .body("samples", hasSize(2))
                .body("samples[0].cpuPercent", equalTo(0.0f))
                .body("samples[0].memoryBytes", equalTo(0))
                .body("samples[0].counters.requests", equalTo(0.0f))
                .body("samples[0].counters.errors", nullValue())
                .body("samples[1].cpuPercent", nullValue())
                .body("samples[1].memoryBytes", nullValue())
                .body("samples[1].counters", anEmptyMap());
    }

    @Test
    void readsOnlyWhatFollowsACursorInsideARangeAndInPages() {
        when(engine.usage(any())).thenReturn(Map.of(ORDERS_KEY, new ResourceUsage(1.0, 1L)));
        telemetry.collect();
        telemetry.collect();
        telemetry.collect();

        var first = alice.read().queryParam("limit", 2).get(metrics()).then().statusCode(200)
                .body("samples", hasSize(2)).body("hasMore", equalTo(true))
                .extract().jsonPath();
        long next = first.getLong("nextAfter");
        alice.read().queryParam("after", next).get(metrics()).then().statusCode(200)
                .body("samples", hasSize(1)).body("hasMore", equalTo(false));
        long last = alice.read().get(metrics()).then().extract().jsonPath().getLong("nextAfter");
        alice.read().queryParam("after", last).get(metrics()).then()
                .body("samples", empty()).body("nextAfter", equalTo((int) last));

        Instant future = Instant.now().plusSeconds(3_600);
        alice.read().queryParam("from", future.toString()).get(metrics()).then().statusCode(200)
                .body("samples", empty());
        alice.read().queryParam("to", Instant.now().minusSeconds(3_600).toString()).get(metrics()).then()
                .statusCode(200).body("samples", empty());
    }

    @Test
    void rejectsInvalidRangesAndPages() {
        Instant now = Instant.now();
        alice.read().queryParam("from", now.toString()).queryParam("to", now.minusSeconds(1).toString())
                .get(metrics()).then().statusCode(400);
        alice.read().queryParam("limit", 0).get(metrics()).then().statusCode(400);
        alice.read().queryParam("limit", 1001).get(metrics()).then().statusCode(400);
        alice.read().queryParam("after", -1).get(metrics()).then().statusCode(400);
        alice.read().queryParam("from", "yesterday").get(metrics()).then().statusCode(400);
    }

    @Test
    void hidesTheMetricsFromOtherAccountsAndVisitors() {
        when(engine.usage(any())).thenReturn(Map.of(ORDERS_KEY, new ResourceUsage(1.0, 1L)));
        telemetry.collect();
        ApiSession bob = ApiSession.signUp(port);

        bob.read().get(metrics()).then().statusCode(404);
        given().port(port).get(metrics()).then().statusCode(401);
    }

    @Test
    void doesNotFindAnUnknownExecution() {
        alice.read().get(scenario + "/executions/" + (execution + 1000) + "/metrics").then().statusCode(404);
    }

    private String metrics() {
        return scenario + "/executions/" + execution + "/metrics";
    }
}
