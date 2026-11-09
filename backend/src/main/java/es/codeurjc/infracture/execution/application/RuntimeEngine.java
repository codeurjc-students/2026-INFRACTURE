package es.codeurjc.infracture.execution.application;

import java.util.Map;

// Applies a plan to Docker and removes it. The reconciler calls it outside any database transaction.
public interface RuntimeEngine {

    // Returns once every node of the plan is healthy; throws if any of them cannot get there. The load
    // waits for startLoad.
    void up(Map<String, Object> plan);

    // Starts the plan's load. Call it once the nodes are healthy; throws if k6 cannot start.
    void startLoad(Map<String, Object> plan);

    // Stops the load if it still runs, so k6 prints its summary, and returns what it left. Safe to repeat.
    LoadReport stopLoad(Map<String, Object> plan);

    // The status of each service of the plan, by service name. A service whose
    // container no longer exists is missing from the result.
    Map<String, ComponentStatus> status(Map<String, Object> plan);

    // Removes the plan's project and anything else carrying all the given labels. Calling it again
    // after a partial cleanup is safe. Returns only when nothing with those labels is left.
    void down(Map<String, Object> plan, Map<String, String> labels);
}
