package es.codeurjc.infracture.execution.application;

import es.codeurjc.infracture.execution.domain.ExecutionPlan;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

// Applies a plan to Docker and removes it. The reconciler calls it outside any database transaction. Only
// the implementation knows how the plan becomes containers; callers name nodes by their key.
public interface RuntimeEngine {

    // Returns once every node of the plan is healthy; throws if any of them cannot get there. The load
    // waits for startLoad. While it waits, it asks cancelled every second and, once it says yes, stops the
    // start and throws StartCancelledException. What the start created is left for down.
    void up(ExecutionPlan plan, BooleanSupplier cancelled);

    // Starts the plan's load. Call it once the nodes are healthy; throws if k6 cannot start. It can be
    // cancelled like up.
    void startLoad(ExecutionPlan plan, BooleanSupplier cancelled);

    // Stops the load if it still runs, so k6 prints its summary, and returns what it left. Safe to repeat.
    LoadReport stopLoad(ExecutionPlan plan);

    // The status of each node and of the load. A node or load whose container no longer exists is missing.
    RuntimeStatus status(ExecutionPlan plan);

    // What the plan's nodes and load printed, oldest first, from since on (everything when since is null).
    // Docker includes lines printed exactly at since, so the caller drops what it already has.
    List<LogLine> logs(ExecutionPlan plan, Instant since);

    // What each running node consumes now, by node key. A node without a reading is missing.
    // It ends within a few seconds, so a Docker that does not answer only costs one sample.
    Map<UUID, ResourceUsage> usage(ExecutionPlan plan);

    // Removes everything the plan created and anything else carrying all of its labels. Calling it again
    // after a partial cleanup is safe. Returns only when nothing with those labels is left.
    void down(ExecutionPlan plan);
}
