package es.codeurjc.infracture.execution.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import es.codeurjc.infracture.execution.domain.ExecutionPlan.CommandProbe;
import es.codeurjc.infracture.execution.domain.ExecutionPlan.Container;
import es.codeurjc.infracture.execution.domain.ExecutionPlan.Counters;
import es.codeurjc.infracture.execution.domain.ExecutionPlan.HttpProbe;
import es.codeurjc.infracture.execution.domain.ExecutionPlan.Limits;
import es.codeurjc.infracture.execution.domain.ExecutionPlan.Node;
import es.codeurjc.infracture.execution.domain.ExecutionPlan.RunAs;
import es.codeurjc.infracture.execution.domain.ExecutionPlan.Wiring;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ExecutionPlanTests {

    private static final UUID ORDERS = UUID.fromString("3f0c8a1e-5b7d-4c2a-9f1e-2d6b8a4c7e10");
    private static final Limits LIMITS = new Limits(0.5, 384L << 20, 200);
    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void readsEachProbeByItsType() throws IOException {
        ExecutionPlan plan;
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("execution/database-crud-plan.json")) {
            plan = json.readValue(input, ExecutionPlan.class);
        }

        assertThat(plan.nodes().getFirst().health()).isEqualTo(new HttpProbe(8080, "/health", 2, 2, 15, 60));
        assertThat(plan.nodes().getLast().health()).isInstanceOf(CommandProbe.class);
    }

    @Test
    void keepsACommandProbeThroughStorage() {
        Node node = new Node(ORDERS, "c-1", new Container("postgres", null, new RunAs(70, 70),
                List.of("/var/lib/postgresql")), new Wiring(Map.of(), Map.of("POSTGRES_PASSWORD", ORDERS), List.of()),
                new CommandProbe(List.of("pg_isready"), 2, 2, 15, 60), null,
                new Counters(URI.create("http://c-1:8080/metrics"), Map.of("requests", "infracture_requests_total")),
                LIMITS);
        ExecutionPlan plan = new ExecutionPlan("p", Map.of(), List.of(node), null);

        assertThat(json.readValue(json.writeValueAsString(plan), ExecutionPlan.class)).isEqualTo(plan);
    }

    // Docker would create the directory as root, and the service could not write to it.
    @Test
    void refusesStorageWithoutAUserToOwnIt() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Container("postgres", null, null, List.of("/var/lib/postgresql")));
    }
}
