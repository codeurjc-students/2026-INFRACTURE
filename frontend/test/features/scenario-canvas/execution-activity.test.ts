import { describe as group, expect, it } from "vitest";

import { executionEventsUrl } from "~/features/scenario-canvas/api/execution-service";
import { describe } from "~/features/scenario-canvas/components/execution-activity";
import type { ExecutionEventDTO } from "~/features/scenario-canvas/model/ExecutionEventDTO";

function event(fields: Partial<ExecutionEventDTO>): ExecutionEventDTO {
  return {
    id: 1,
    executionId: 12,
    componentKey: null,
    kind: "EVENT",
    at: "2026-11-21T10:00:00Z",
    ...fields,
  };
}

group("execution activity", () => {
  it("says why the experiment stops, with the failure reason when there is one", () => {
    expect(describe(event({ type: "STOP", cause: "TIME_LIMIT" }))).toBe(
      "Time limit reached",
    );
    expect(
      describe(
        event({
          type: "STOP",
          cause: "FAILURE",
          reason: "orders-api is unhealthy",
        }),
      ),
    ).toBe("Failed: orders-api is unhealthy");
  });

  it("describes cleanups, responses, left-out lines, states and log lines", () => {
    expect(
      describe(
        event({
          type: "CLEANUP_FAILED",
          attempt: 2,
          error: "network has active endpoints",
        }),
      ),
    ).toBe("Cleanup failed (attempt 2): network has active endpoints");
    expect(describe(event({ type: "RESPONSE", response: "{}" }))).toBe(
      "Answered {}",
    );
    expect(describe(event({ type: "LOGS_OMITTED", count: 30 }))).toBe(
      "30 more log lines left out",
    );
    expect(describe(event({ kind: "STATE", status: "EXITED" }))).toBe(
      "Stopped",
    );
    expect(
      describe(event({ kind: "LOG", line: "password=[redacted]" })),
    ).toBe("password=[redacted]");
  });

  it("resumes the stream after a cursor only when there is one", () => {
    expect(executionEventsUrl("7", "3", 12)).toBe(
      "/api/v1/projects/7/scenarios/3/executions/12/events",
    );
    expect(executionEventsUrl("7", "3", 12, 0)).toBe(
      "/api/v1/projects/7/scenarios/3/executions/12/events",
    );
    expect(executionEventsUrl("7", "3", 12, 40)).toBe(
      "/api/v1/projects/7/scenarios/3/executions/12/events?after=40",
    );
  });
});
